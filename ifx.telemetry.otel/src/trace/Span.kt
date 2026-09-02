package ifx.telemetry.otel.trace

import ifx.telemetry.otel.TelemetryResource
import kotlinx.coroutines.sync.Mutex

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
    val resource: TelemetryResource,
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
) {
    constructor(
        serviceName: String,
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        traceFlags: String,
        traceState: String? = null,
        name: String,
        kind: SpanKind,
        startTimeUnixNano: Long,
        endTimeUnixNano: Long,
        attributes: Map<String, String>,
        error: SpanError? = null,
    ) : this(
        resource = TelemetryResource(serviceName),
        traceId = traceId,
        spanId = spanId,
        parentSpanId = parentSpanId,
        traceFlags = traceFlags,
        traceState = traceState,
        name = name,
        kind = kind,
        startTimeUnixNano = startTimeUnixNano,
        endTimeUnixNano = endTimeUnixNano,
        attributes = attributes,
        error = error,
    )

    val serviceName: String get() = resource.serviceName
}

fun interface SpanExporter {
    suspend fun export(span: FinishedSpan)

    suspend fun export(spans: List<FinishedSpan>) {
        spans.forEach { export(it) }
    }

    suspend fun shutdown() = Unit
}

interface SpanProcessor {
    suspend fun onEnd(span: FinishedSpan)

    suspend fun flush()

    suspend fun shutdown()
}

class SimpleSpanProcessor(
    private val exporter: SpanExporter,
) : SpanProcessor {
    private val mutex = Mutex()
    private var shutDown = false

    override suspend fun onEnd(span: FinishedSpan) {
        mutex.lock()
        try {
            if (!shutDown) exporter.export(span)
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun flush() {
        mutex.lock()
        mutex.unlock()
    }

    override suspend fun shutdown() {
        mutex.lock()
        try {
            if (!shutDown) {
                shutDown = true
                exporter.shutdown()
            }
        } finally {
            mutex.unlock()
        }
    }
}
