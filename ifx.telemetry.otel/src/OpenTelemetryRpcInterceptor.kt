package ifx.telemetry.otel

import ifx.logging.Log
import ifx.logging.LogTag
import ifx.logging.ServiceLogScope
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.telemetry.otel.internal.ActiveSpan
import ifx.telemetry.otel.internal.RemoteTraceParent
import ifx.telemetry.otel.internal.traceParentOrNull
import ifx.telemetry.otel.internal.withTraceParent
import ifx.telemetry.otel.metric.RpcCallMeasurement
import ifx.telemetry.otel.metric.RpcMetricRecorder
import ifx.telemetry.otel.trace.ParentBasedSampler
import ifx.telemetry.otel.trace.Sampler
import ifx.telemetry.otel.trace.SimpleSpanProcessor
import ifx.telemetry.otel.trace.SpanExporter
import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.SpanProcessor
import ifx.telemetry.otel.trace.Tracer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

class OpenTelemetryRpcInterceptor(
    private val tracer: Tracer,
    private val resource: TelemetryResource,
    private val metricRecorder: RpcMetricRecorder? = null,
    private val logRpcCalls: Boolean = false,
) : IInterceptor {
    private val rpcLog = Log(LogTag(display = false, retained = false))

    constructor(
        spanProcessor: SpanProcessor,
        resource: TelemetryResource,
        sampler: Sampler = ParentBasedSampler(),
        onObservabilityFailure: suspend (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
        logRpcCalls: Boolean = false,
    ) : this(
        Tracer(spanProcessor, resource, sampler, onObservabilityFailure),
        resource,
        metricRecorder,
        logRpcCalls,
    )

    constructor(
        spanProcessor: SpanProcessor,
        serviceName: String,
        sampler: Sampler = ParentBasedSampler(),
        onObservabilityFailure: suspend (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
        logRpcCalls: Boolean = false,
    ) : this(
        spanProcessor,
        TelemetryResource(serviceName),
        sampler,
        onObservabilityFailure,
        metricRecorder,
        logRpcCalls,
    )

    constructor(
        exporter: SpanExporter,
        resource: TelemetryResource,
        sampler: Sampler = ParentBasedSampler(),
        onObservabilityFailure: suspend (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
        logRpcCalls: Boolean = false,
    ) : this(
        SimpleSpanProcessor(exporter),
        resource,
        sampler,
        onObservabilityFailure,
        metricRecorder,
        logRpcCalls,
    )

    constructor(
        exporter: SpanExporter,
        serviceName: String,
        sampler: Sampler = ParentBasedSampler(),
        onObservabilityFailure: suspend (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
        logRpcCalls: Boolean = false,
    ) : this(
        SimpleSpanProcessor(exporter),
        TelemetryResource(serviceName),
        sampler,
        onObservabilityFailure,
        metricRecorder,
        logRpcCalls,
    )

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val callingService = if (call.direction == CallDirection.CLIENT) {
            ServiceLogScope.currentOrNull()?.serviceInterface
        } else {
            null
        }
        val parent = when (call.direction) {
            CallDirection.CLIENT -> currentCoroutineContext()[ActiveSpan]
                ?: call.message.traceParentOrNull()?.toActiveSpan()

            CallDirection.SERVER -> call.message.traceParentOrNull()?.toActiveSpan()
        }
        val rpcMethod = "${call.service}/${call.operation}"
        val spanKind = when (call.direction) {
            CallDirection.CLIENT -> SpanKind.CLIENT
            CallDirection.SERVER -> SpanKind.SERVER
        }
        val attributes = mapOf(
            "rpc.system.name" to "ifx",
            "rpc.method" to rpcMethod,
            "ifx.interaction.type" to call.interactionType.name.lowercase(),
        )
        val startMark = TimeSource.Monotonic.markNow()
        var failure: Throwable? = null

        try {
            tracer.spanFlow(
                name = rpcMethod,
                kind = spanKind,
                attributes = attributes,
                parent = parent,
            ) {
                val request = when (call.direction) {
                    CallDirection.CLIENT -> call.message.withTraceParent(context)
                    CallDirection.SERVER -> call.message
                }
                flow {
                    logRequest(call, callingService, request)
                    next(call.copy(message = request)).collect { response ->
                        logResponse(call, callingService, response)
                        emit(response)
                    }
                }
            }.collect { emit(it) }
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            try {
                metricRecorder?.record(
                    RpcCallMeasurement(
                        resource = resource,
                        direction = call.direction,
                        rpcMethod = rpcMethod,
                        interactionType = call.interactionType,
                        durationSeconds = startMark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0,
                        errorType = failure?.errorType(),
                    ),
                )
            } catch (metricFailure: Throwable) {
                tracer.reportFailure(metricFailure)
            }
        }
    }

    private suspend fun logRequest(call: InterceptorCall, callingService: String?, request: Message) {
        val direction = when (call.direction) {
            CallDirection.CLIENT -> callingService
                ?.let { "${it.substringAfterLast('.')} ->" }
                ?: "Client ->"
            CallDirection.SERVER -> "->"
        }
        logRpcCall { "$direction ${call.callName()}: $request" }
    }

    private suspend fun logResponse(call: InterceptorCall, callingService: String?, response: Message) {
        val message = when (call.direction) {
            CallDirection.CLIENT -> callingService
                ?.let { "${it.substringAfterLast('.')} <- ${call.callName()}: $response" }
                ?: "Client <- ${call.callName()}: $response"
            CallDirection.SERVER -> "${call.callName()} -> $response"
        }
        logRpcCall { message }
    }

    private suspend fun logRpcCall(message: () -> String) {
        if (!logRpcCalls) return
        try {
            rpcLog.info(message = message)
        } catch (loggingFailure: Throwable) {
            tracer.reportFailure(loggingFailure)
        }
    }
}

private fun InterceptorCall.callName(): String = "${service.substringAfterLast('.')}.${operation}"

private fun RemoteTraceParent.toActiveSpan(): ActiveSpan = ActiveSpan(
    traceId = traceId,
    spanId = spanId,
    traceFlags = traceFlags,
    traceState = traceState,
    isRemote = true,
)

private fun Throwable.errorType(): String = this::class.qualifiedName ?: this::class.simpleName ?: "Throwable"
