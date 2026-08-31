package ifx.telemetry.otel

import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Clock

class OpenTelemetryInterceptor(
    private val exporter: SpanExporter,
    private val serviceName: String,
    private val sampled: Boolean = true,
    private val onExportFailure: (Throwable) -> Unit = {},
) : IInterceptor {
    init {
        require(serviceName.isNotBlank()) { "OpenTelemetry serviceName must not be blank" }
    }

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
        var failure: Throwable? = null

        try {
            emitAll(next(call.copy(message = request)).flowOn(activeSpan))
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            if (activeSpan.traceFlags.isSampled()) {
                val span = call.finishedSpan(serviceName, activeSpan, parent, startTime, failure)
                try {
                    exporter.export(span)
                } catch (exportFailure: Throwable) {
                    try {
                        onExportFailure(exportFailure)
                    } catch (_: Throwable) {
                        // Telemetry must not alter the outcome of the instrumented RPC.
                    }
                }
            }
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
    serviceName: String,
    span: ActiveSpan,
    parent: ActiveSpan?,
    startTime: Long,
    failure: Throwable?,
): FinishedSpan {
    val rpcMethod = "$service/$operation"
    return FinishedSpan(
        serviceName = serviceName,
        traceId = span.traceId,
        spanId = span.spanId,
        parentSpanId = parent?.spanId,
        traceFlags = span.traceFlags,
        traceState = span.traceState,
        name = "${service.substringAfterLast('.')}.$operation",
        kind = when (direction) {
            CallDirection.CLIENT -> SpanKind.CLIENT
            CallDirection.SERVER -> SpanKind.SERVER
        },
        startTimeUnixNano = startTime,
        endTimeUnixNano = unixTimeNanos(),
        attributes = buildMap {
            put("rpc.system.name", "ifx")
            put("rpc.method", rpcMethod)
            put("ifx.interaction.type", interactionType.name.lowercase())
            failure?.let { put("error.type", it::class.qualifiedName ?: it::class.simpleName ?: "Throwable") }
        },
        error = failure?.let {
            SpanError(
                type = it::class.qualifiedName ?: it::class.simpleName ?: "Throwable",
                message = it.message,
                stackTrace = it.stackTraceToString(),
            )
        },
    )
}

private fun unixTimeNanos(): Long {
    val now = Clock.System.now()
    return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
}
