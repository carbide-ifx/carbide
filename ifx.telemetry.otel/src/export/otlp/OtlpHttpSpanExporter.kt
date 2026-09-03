package ifx.telemetry.otel.export.otlp

import ifx.telemetry.otel.INSTRUMENTATION_SCOPE_NAME
import ifx.telemetry.otel.TELEMETRY_SDK_VERSION
import ifx.telemetry.otel.internal.isSampled
import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.SpanExporter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OtlpHttpSpanExporter(
    private val endpoint: String = "http://localhost:4318/v1/traces",
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: HttpClient = HttpClient(),
) : SpanExporter {
    override suspend fun export(span: FinishedSpan) {
        export(listOf(span))
    }

    override suspend fun export(spans: List<FinishedSpan>) {
        if (spans.isEmpty()) return
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            this@OtlpHttpSpanExporter.headers.forEach { (name, value) -> header(name, value) }
            setBody(spans.toOtlpJson())
        }
        if (!response.status.isSuccess()) {
            throw OtlpExportException(response.status, response.body<String>())
        }
    }

    fun close() {
        httpClient.close()
    }

    override suspend fun shutdown() = close()
}

class OtlpExportException(
    val status: HttpStatusCode,
    responseBody: String,
) : RuntimeException("OTLP export failed with HTTP ${status.value}: $responseBody")

private val OtlpJson = Json {
    encodeDefaults = false
    explicitNulls = false
}

internal fun FinishedSpan.toOtlpJson(): String = listOf(this).toOtlpJson()

internal fun List<FinishedSpan>.toOtlpJson(): String = OtlpJson.encodeToString(
    ExportTraceServiceRequest(
        resourceSpans = groupBy(FinishedSpan::resource).map { (resource, spans) ->
            ResourceSpans(
                resource = Resource(
                    attributes = buildMap {
                        putAll(resource.otelAttributes())
                        put("telemetry.sdk.name", "opentelemetry")
                        put("telemetry.sdk.language", "kotlin")
                        put("telemetry.sdk.version", TELEMETRY_SDK_VERSION)
                    }.map { (key, value) -> KeyValue(key, AnyValue(stringValue = value)) },
                ),
                scopeSpans = listOf(
                    ScopeSpans(
                        scope = InstrumentationScope(
                            name = INSTRUMENTATION_SCOPE_NAME,
                            version = TELEMETRY_SDK_VERSION,
                        ),
                        spans = spans.map(FinishedSpan::toOtlpSpan),
                    ),
                ),
            )
        },
    ),
)

private fun FinishedSpan.toOtlpSpan(): OtlpSpan = OtlpSpan(
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId,
    traceState = traceState,
    flags = if (traceFlags.isSampled()) 1 else 0,
    name = name,
    kind = kind.otlpValue,
    startTimeUnixNano = startTimeUnixNano.toString(),
    endTimeUnixNano = endTimeUnixNano.toString(),
    attributes = attributes.map { (key, value) -> KeyValue(key, AnyValue(stringValue = value)) },
    links = links.map { link ->
        OtlpLink(
            traceId = link.context.traceId,
            spanId = link.context.spanId,
            traceState = link.context.traceState,
            attributes = link.attributes.map { (key, value) -> KeyValue(key, AnyValue(stringValue = value)) },
            flags = if (link.context.traceFlags.isSampled()) 1 else 0,
        )
    },
    droppedLinksCount = droppedLinksCount,
    events = error?.let {
        listOf(
            SpanEvent(
                timeUnixNano = endTimeUnixNano.toString(),
                name = "exception",
                attributes = listOfNotNull(
                    KeyValue("exception.type", AnyValue(stringValue = it.type)),
                    it.message?.let { message -> KeyValue("exception.message", AnyValue(stringValue = message)) },
                    KeyValue("exception.stacktrace", AnyValue(stringValue = it.stackTrace)),
                ),
            ),
        )
    }.orEmpty(),
    status = error?.let { SpanStatus(message = it.message, code = 2) },
)

@Serializable
private data class ExportTraceServiceRequest(val resourceSpans: List<ResourceSpans>)

@Serializable
private data class ResourceSpans(val resource: Resource, val scopeSpans: List<ScopeSpans>)

@Serializable
private data class Resource(val attributes: List<KeyValue>)

@Serializable
private data class ScopeSpans(val scope: InstrumentationScope, val spans: List<OtlpSpan>)

@Serializable
private data class InstrumentationScope(val name: String, val version: String? = null)

@Serializable
private data class OtlpSpan(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val traceState: String? = null,
    val flags: Int,
    val name: String,
    val kind: Int,
    val startTimeUnixNano: String,
    val endTimeUnixNano: String,
    val attributes: List<KeyValue>,
    val links: List<OtlpLink> = emptyList(),
    val droppedLinksCount: Int = 0,
    val events: List<SpanEvent> = emptyList(),
    val status: SpanStatus? = null,
)

@Serializable
private data class OtlpLink(
    val traceId: String,
    val spanId: String,
    val traceState: String? = null,
    val attributes: List<KeyValue> = emptyList(),
    val flags: Int,
)

@Serializable
private data class SpanEvent(
    val timeUnixNano: String,
    val name: String,
    val attributes: List<KeyValue>,
)

@Serializable
private data class SpanStatus(val message: String? = null, val code: Int)

@Serializable
private data class KeyValue(val key: String, val value: AnyValue)

@Serializable
private data class AnyValue(val stringValue: String? = null)
