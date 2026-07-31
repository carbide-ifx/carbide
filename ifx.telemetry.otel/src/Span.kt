package ifx.telemetry.otel

import kotlin.coroutines.CoroutineContext

enum class SpanKind(internal val otlpValue: Int) {
    SERVER(2),
    CLIENT(3),
}

data class SpanError(
    val type: String,
    val message: String?,
    val stackTrace: String,
)

data class FinishedSpan(
    val serviceName: String,
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val traceFlags: String,
    val traceState: String? = null,
    val name: String,
    val kind: SpanKind,
    val startTimeUnixNano: Long,
    val endTimeUnixNano: Long,
    val attributes: Map<String, String>,
    val error: SpanError? = null,
)

fun interface SpanExporter {
    suspend fun export(span: FinishedSpan)
}

internal data class ActiveSpan(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
    val traceState: String?,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<ActiveSpan> get() = Key

    companion object Key : CoroutineContext.Key<ActiveSpan>
}
