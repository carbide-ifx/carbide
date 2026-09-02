package ifx.telemetry.otel.export.otlp

import ifx.telemetry.otel.INSTRUMENTATION_SCOPE_NAME
import ifx.telemetry.otel.TELEMETRY_SDK_VERSION
import ifx.telemetry.otel.metric.HistogramMetric
import ifx.telemetry.otel.metric.MetricExporter
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

class OtlpHttpMetricExporter(
    private val endpoint: String = "http://localhost:4318/v1/metrics",
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: HttpClient = HttpClient(),
) : MetricExporter {
    override suspend fun export(metrics: List<HistogramMetric>) {
        if (metrics.isEmpty()) return
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            this@OtlpHttpMetricExporter.headers.forEach { (name, value) -> header(name, value) }
            setBody(metrics.toOtlpJson())
        }
        if (!response.status.isSuccess()) {
            throw OtlpMetricExportException(response.status, response.body<String>())
        }
    }

    fun close() = httpClient.close()

    override suspend fun shutdown() = close()
}

class OtlpMetricExportException(
    val status: HttpStatusCode,
    responseBody: String,
) : RuntimeException("OTLP metric export failed with HTTP ${status.value}: $responseBody")

private val MetricJson = Json {
    encodeDefaults = false
    explicitNulls = false
}

internal fun List<HistogramMetric>.toOtlpJson(): String = MetricJson.encodeToString(
    ExportMetricsServiceRequest(
        resourceMetrics = groupBy(HistogramMetric::resource).map { (resource, resourceMetrics) ->
            ResourceMetrics(
                resource = MetricResource(
                    attributes = buildMap {
                        putAll(resource.otelAttributes())
                        put("telemetry.sdk.name", "opentelemetry")
                        put("telemetry.sdk.language", "kotlin")
                        put("telemetry.sdk.version", TELEMETRY_SDK_VERSION)
                    }.map { (key, value) -> MetricKeyValue(key, MetricAnyValue(stringValue = value)) },
                ),
                scopeMetrics = listOf(
                    ScopeMetrics(
                        scope = MetricInstrumentationScope(
                            name = INSTRUMENTATION_SCOPE_NAME,
                            version = TELEMETRY_SDK_VERSION,
                        ),
                        metrics = resourceMetrics.groupBy(HistogramMetric::name).map { (name, series) ->
                            Metric(
                                name = name,
                                unit = series.first().unit,
                                histogram = Histogram(
                                    aggregationTemporality = 2,
                                    dataPoints = series.map(HistogramMetric::toDataPoint),
                                ),
                            )
                        },
                    ),
                ),
            )
        },
    ),
)

private fun HistogramMetric.toDataPoint(): HistogramDataPoint = HistogramDataPoint(
    attributes = attributes.map { (key, value) -> MetricKeyValue(key, MetricAnyValue(stringValue = value)) },
    startTimeUnixNano = startTimeUnixNano.toString(),
    timeUnixNano = timeUnixNano.toString(),
    count = count.toString(),
    sum = sum,
    bucketCounts = bucketCounts.map(Long::toString),
    explicitBounds = explicitBounds,
    min = min,
    max = max,
)

@Serializable
private data class ExportMetricsServiceRequest(val resourceMetrics: List<ResourceMetrics>)

@Serializable
private data class ResourceMetrics(
    val resource: MetricResource,
    val scopeMetrics: List<ScopeMetrics>,
)

@Serializable
private data class MetricResource(val attributes: List<MetricKeyValue>)

@Serializable
private data class ScopeMetrics(
    val scope: MetricInstrumentationScope,
    val metrics: List<Metric>,
)

@Serializable
private data class MetricInstrumentationScope(val name: String, val version: String? = null)

@Serializable
private data class Metric(val name: String, val unit: String, val histogram: Histogram)

@Serializable
private data class Histogram(
    val aggregationTemporality: Int,
    val dataPoints: List<HistogramDataPoint>,
)

@Serializable
private data class HistogramDataPoint(
    val attributes: List<MetricKeyValue>,
    val startTimeUnixNano: String,
    val timeUnixNano: String,
    val count: String,
    val sum: Double,
    val bucketCounts: List<String>,
    val explicitBounds: List<Double>,
    val min: Double,
    val max: Double,
)

@Serializable
private data class MetricKeyValue(val key: String, val value: MetricAnyValue)

@Serializable
private data class MetricAnyValue(val stringValue: String? = null)
