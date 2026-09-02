package ifx.telemetry.otel

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
import ifx.telemetry.otel.trace.SimpleSpanProcessor
import ifx.telemetry.otel.trace.SpanError
import ifx.telemetry.otel.trace.SpanExporter
import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.SpanProcessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.TimeSource

class OpenTelemetryRpcInterceptor(
    private val spanProcessor: SpanProcessor,
    private val resource: TelemetryResource,
    private val sampled: Boolean = true,
    private val onExportFailure: (Throwable) -> Unit = {},
    private val metricRecorder: RpcMetricRecorder? = null,
) : IInterceptor {
    constructor(
        spanProcessor: SpanProcessor,
        serviceName: String,
        sampled: Boolean = true,
        onExportFailure: (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
    ) : this(spanProcessor, TelemetryResource(serviceName), sampled, onExportFailure, metricRecorder)

    constructor(
        exporter: SpanExporter,
        resource: TelemetryResource,
        sampled: Boolean = true,
        onExportFailure: (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
    ) : this(SimpleSpanProcessor(exporter), resource, sampled, onExportFailure, metricRecorder)

    constructor(
        exporter: SpanExporter,
        serviceName: String,
        sampled: Boolean = true,
        onExportFailure: (Throwable) -> Unit = {},
        metricRecorder: RpcMetricRecorder? = null,
    ) : this(
        SimpleSpanProcessor(exporter),
        TelemetryResource(serviceName),
        sampled,
        onExportFailure,
        metricRecorder,
    )

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val parent = when (call.direction) {
            CallDirection.CLIENT -> currentCoroutineContext()[ActiveSpan]
                ?: call.message.traceParentOrNull()?.toActiveSpan()

            CallDirection.SERVER -> call.message.traceParentOrNull()?.toActiveSpan()
        }
        val activeSpan = ActiveSpan(
            traceId = parent?.traceId ?: newTraceId(),
            spanId = newSpanId(),
            traceFlags = parent?.traceFlags ?: if (sampled) "01" else "00",
            traceState = parent?.traceState,
        )
        val request = when (call.direction) {
            CallDirection.CLIENT -> call.message.withTraceParent(activeSpan)
            CallDirection.SERVER -> call.message
        }
        val startTime = unixTimeNanos()
        val startMark = TimeSource.Monotonic.markNow()
        var failure: Throwable? = null

        try {
            emitAll(next(call.copy(message = request)).flowOn(activeSpan))
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            val durationNanos = startMark.elapsedNow().inWholeNanoseconds
            val rpcMethod = "${call.service}/${call.operation}"
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
                reportTelemetryFailure(metricFailure)
            }
            if (activeSpan.traceFlags.isSampled()) {
                val span = call.finishedSpan(
                    resource,
                    activeSpan,
                    parent,
                    startTime,
                    startTime + durationNanos,
                    rpcMethod,
                    errorType,
                    failure,
                )
                try {
                    spanProcessor.onEnd(span)
                } catch (exportFailure: Throwable) {
                    reportTelemetryFailure(exportFailure)
                }
            }
        }
    }

    private fun reportTelemetryFailure(failure: Throwable) {
        try {
            onExportFailure(failure)
        } catch (_: Throwable) {
            // Telemetry must not alter the outcome of the instrumented RPC.
        }
    }
}

private fun RemoteTraceParent.toActiveSpan(): ActiveSpan = ActiveSpan(
    traceId = traceId,
    spanId = spanId,
    traceFlags = traceFlags,
    traceState = traceState,
)

private fun InterceptorCall.finishedSpan(
    resource: TelemetryResource,
    span: ActiveSpan,
    parent: ActiveSpan?,
    startTime: Long,
    endTime: Long,
    rpcMethod: String,
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
        kind = when (direction) {
            CallDirection.CLIENT -> SpanKind.CLIENT
            CallDirection.SERVER -> SpanKind.SERVER
        },
        startTimeUnixNano = startTime,
        endTimeUnixNano = endTime,
        attributes = buildMap {
            put("rpc.system.name", "ifx")
            put("rpc.method", rpcMethod)
            put("ifx.interaction.type", interactionType.name.lowercase())
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
