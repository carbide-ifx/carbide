package ifx.telemetry.otel

import ifx.logging.Log
import ifx.logging.LogCorrelation
import ifx.logging.LogTag
import ifx.logging.ServiceLogScope
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.telemetry.otel.internal.ActiveSpan
import ifx.telemetry.otel.internal.RemoteTraceParent
import ifx.telemetry.otel.internal.isSampled
import ifx.telemetry.otel.internal.newSpanId
import ifx.telemetry.otel.internal.newTraceId
import ifx.telemetry.otel.internal.traceParentOrNull
import ifx.telemetry.otel.internal.unixTimeNanos
import ifx.telemetry.otel.internal.withTraceParent
import ifx.telemetry.otel.metric.RpcCallMeasurement
import ifx.telemetry.otel.metric.RpcMetricRecorder
import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.ParentBasedSampler
import ifx.telemetry.otel.trace.Sampler
import ifx.telemetry.otel.trace.SamplingContext
import ifx.telemetry.otel.trace.SamplingDecision
import ifx.telemetry.otel.trace.SamplingParent
import ifx.telemetry.otel.trace.SimpleSpanProcessor
import ifx.telemetry.otel.trace.SpanError
import ifx.telemetry.otel.trace.SpanExporter
import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.SpanProcessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.TimeSource

class OpenTelemetryRpcInterceptor(
    private val spanProcessor: SpanProcessor,
    private val resource: TelemetryResource,
    private val sampler: Sampler = ParentBasedSampler(),
    private val onObservabilityFailure: suspend (Throwable) -> Unit = {},
    private val metricRecorder: RpcMetricRecorder? = null,
    private val logRpcCalls: Boolean = false,
) : IInterceptor {
    private val rpcLog = Log(LogTag(display = false, retained = false))

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
        val traceId = parent?.traceId ?: newTraceId()
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
        val samplingContext = SamplingContext(
            parent = parent?.toSamplingParent(),
            traceId = traceId,
            name = rpcMethod,
            kind = spanKind,
            attributes = attributes,
        )
        val samplingDecision = try {
            sampler.shouldSample(samplingContext)
        } catch (samplingFailure: Throwable) {
            reportObservabilityFailure(samplingFailure)
            samplingContext.parent.inheritedDecision()
        }
        val activeSpan = ActiveSpan(
            traceId = traceId,
            spanId = newSpanId(),
            traceFlags = parent?.traceFlags.withSamplingDecision(samplingDecision),
            traceState = parent?.traceState,
        )
        val logCorrelation = LogCorrelation(
            traceId = activeSpan.traceId,
            spanId = activeSpan.spanId,
            traceFlags = activeSpan.traceFlags,
        )
        val request = when (call.direction) {
            CallDirection.CLIENT -> call.message.withTraceParent(activeSpan)
            CallDirection.SERVER -> call.message
        }
        val startTime = unixTimeNanos()
        val startMark = TimeSource.Monotonic.markNow()
        var failure: Throwable? = null

        try {
            logRequest(call, callingService, request, logCorrelation)
            next(call.copy(message = request))
                .flowOn(activeSpan + logCorrelation)
                .collect { response ->
                    logResponse(call, callingService, response, logCorrelation)
                    emit(response)
                }
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            val durationNanos = startMark.elapsedNow().inWholeNanoseconds
            val errorType = failure?.errorType()
            try {
                metricRecorder?.record(
                    RpcCallMeasurement(
                        resource = resource,
                        direction = call.direction,
                        rpcMethod = rpcMethod,
                        interactionType = call.interactionType,
                        durationSeconds = durationNanos / 1_000_000_000.0,
                        errorType = errorType,
                    ),
                )
            } catch (metricFailure: Throwable) {
                reportObservabilityFailure(metricFailure)
            }
            if (activeSpan.traceFlags.isSampled()) {
                val span = call.finishedSpan(
                    resource,
                    activeSpan,
                    parent,
                    startTime,
                    startTime + durationNanos,
                    rpcMethod,
                    spanKind,
                    attributes,
                    errorType,
                    failure,
                )
                try {
                    spanProcessor.onEnd(span)
                } catch (exportFailure: Throwable) {
                    reportObservabilityFailure(exportFailure)
                }
            }
        }
    }

    private suspend fun reportObservabilityFailure(failure: Throwable) {
        try {
            onObservabilityFailure(failure)
        } catch (_: Throwable) {
            // Telemetry must not alter the outcome of the instrumented RPC.
        }
    }

    private suspend fun logRequest(
        call: InterceptorCall,
        callingService: String?,
        request: Message,
        correlation: LogCorrelation,
    ) {
        val direction = when (call.direction) {
            CallDirection.CLIENT -> callingService
                ?.let { "${it.substringAfterLast('.')} ->" }
                ?: "Client ->"
            CallDirection.SERVER -> "->"
        }
        logRpcCall(correlation) { "$direction ${call.callName()}: $request" }
    }

    private suspend fun logResponse(
        call: InterceptorCall,
        callingService: String?,
        response: Message,
        correlation: LogCorrelation,
    ) {
        val message = when (call.direction) {
            CallDirection.CLIENT -> callingService
                ?.let { "${it.substringAfterLast('.')} <- ${call.callName()}: $response" }
                ?: "Client <- ${call.callName()}: $response"
            CallDirection.SERVER -> "${call.callName()} -> $response"
        }
        logRpcCall(correlation) { message }
    }

    private suspend fun logRpcCall(correlation: LogCorrelation, message: () -> String) {
        if (!logRpcCalls) return
        try {
            rpcLog.withCorrelation(correlation).info(message = message)
        } catch (loggingFailure: Throwable) {
            reportObservabilityFailure(loggingFailure)
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

private fun ActiveSpan.toSamplingParent(): SamplingParent = SamplingParent(
    traceId = traceId,
    spanId = spanId,
    traceFlags = traceFlags,
    traceState = traceState,
    isRemote = isRemote,
)

private fun SamplingParent?.inheritedDecision(): SamplingDecision = when {
    this == null -> SamplingDecision.DROP
    isSampled -> SamplingDecision.RECORD_AND_SAMPLE
    else -> SamplingDecision.DROP
}

private fun String?.withSamplingDecision(decision: SamplingDecision): String {
    val inheritedFlags = this?.toIntOrNull(16) ?: 0
    val flags = when (decision) {
        SamplingDecision.DROP -> inheritedFlags and 0xfe
        SamplingDecision.RECORD_AND_SAMPLE -> inheritedFlags or 0x01
    }
    return flags.toString(16).padStart(2, '0')
}

private fun InterceptorCall.finishedSpan(
    resource: TelemetryResource,
    span: ActiveSpan,
    parent: ActiveSpan?,
    startTime: Long,
    endTime: Long,
    rpcMethod: String,
    spanKind: SpanKind,
    initialAttributes: Map<String, String>,
    errorType: String?,
    failure: Throwable?,
): FinishedSpan {
    return FinishedSpan(
        resource = resource,
        traceId = span.traceId,
        spanId = span.spanId,
        parentSpanId = parent?.spanId,
        traceFlags = span.traceFlags,
        traceState = span.traceState,
        name = rpcMethod,
        kind = spanKind,
        startTimeUnixNano = startTime,
        endTimeUnixNano = endTime,
        attributes = buildMap {
            putAll(initialAttributes)
            errorType?.let { put("error.type", it) }
        },
        error = failure?.let {
            SpanError(
                type = requireNotNull(errorType),
                message = it.message,
                stackTrace = it.stackTraceToString(),
            )
        },
    )
}

private fun Throwable.errorType(): String = this::class.qualifiedName ?: this::class.simpleName ?: "Throwable"
