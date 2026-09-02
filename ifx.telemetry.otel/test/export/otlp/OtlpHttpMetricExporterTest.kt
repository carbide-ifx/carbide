package ifx.telemetry.otel.export.otlp

import ifx.telemetry.otel.TelemetryResource
import ifx.telemetry.otel.metric.HistogramMetric
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OtlpHttpMetricExporterTest {
    @Test
    fun `exports an OTLP cumulative histogram`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            assertEquals("http://collector:4318/v1/metrics", request.url.toString())
            assertEquals(ContentType.Application.Json, request.body.contentType)
            assertEquals("secret", request.headers["x-api-key"])
            requestBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK)
        }
        val exporter = OtlpHttpMetricExporter(
            endpoint = "http://collector:4318/v1/metrics",
            headers = mapOf("x-api-key" to "secret"),
            httpClient = HttpClient(engine),
        )

        exporter.export(listOf(testMetric()))
        exporter.shutdown()

        assertContains(requestBody, "\"resourceMetrics\"")
        assertContains(requestBody, "\"name\":\"rpc.client.call.duration\"")
        assertContains(requestBody, "\"unit\":\"s\"")
        assertContains(requestBody, "\"aggregationTemporality\":2")
        assertContains(requestBody, "\"count\":\"2\"")
        assertContains(requestBody, "\"sum\":0.03")
        assertContains(requestBody, "\"bucketCounts\":[\"1\",\"1\"]")
        assertContains(requestBody, "\"rpc.method\"")
        assertContains(requestBody, "\"service.name\"")
    }
}

private fun testMetric() = HistogramMetric(
    resource = TelemetryResource("sales-service"),
    name = "rpc.client.call.duration",
    unit = "s",
    attributes = mapOf(
        "rpc.system.name" to "ifx",
        "rpc.method" to "manager.sales.contract.ISalesManager/listProducts()",
    ),
    startTimeUnixNano = 1_000_000_001,
    timeUnixNano = 1_000_000_101,
    count = 2,
    sum = 0.03,
    min = 0.01,
    max = 0.02,
    explicitBounds = listOf(0.01),
    bucketCounts = listOf(1, 1),
)
