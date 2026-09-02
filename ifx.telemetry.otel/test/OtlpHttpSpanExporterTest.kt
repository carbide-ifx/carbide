package ifx.telemetry.otel

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OtlpHttpSpanExporterTest {
    @Test
    fun `exporter posts OTLP JSON to the configured endpoint`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            assertEquals("http://collector:4318/v1/traces", request.url.toString())
            assertEquals(ContentType.Application.Json, request.body.contentType)
            assertEquals("secret", request.headers["x-api-key"])
            requestBody = request.body.toByteArray().decodeToString()
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val exporter = OtlpHttpSpanExporter(
            endpoint = "http://collector:4318/v1/traces",
            headers = mapOf("x-api-key" to "secret"),
            httpClient = HttpClient(engine),
        )

        exporter.export(testSpan())
        exporter.close()

        assertContains(requestBody, "\"resourceSpans\"")
        assertContains(requestBody, "\"service.name\"")
        assertContains(requestBody, "\"stringValue\":\"sales-service\"")
        assertContains(requestBody, "\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"")
        assertContains(requestBody, "\"spanId\":\"00f067aa0ba902b7\"")
        assertContains(requestBody, "\"traceState\":\"vendor=value\"")
        assertContains(requestBody, "\"kind\":3")
        assertContains(requestBody, "\"startTimeUnixNano\":\"1000000001\"")
    }

    @Test
    fun `batch export sends one request containing every span`() = runBlocking {
        var requests = 0
        var requestBody = ""
        val engine = MockEngine { request ->
            requests += 1
            requestBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK)
        }
        val exporter = OtlpHttpSpanExporter(httpClient = HttpClient(engine))

        exporter.export(listOf(testSpan(), testSpan().copy(spanId = "10f067aa0ba902b7")))
        exporter.shutdown()

        assertEquals(1, requests)
        assertContains(requestBody, "\"spanId\":\"00f067aa0ba902b7\"")
        assertContains(requestBody, "\"spanId\":\"10f067aa0ba902b7\"")
    }
}

private fun testSpan(): FinishedSpan = FinishedSpan(
    serviceName = "sales-service",
    traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
    spanId = "00f067aa0ba902b7",
    parentSpanId = null,
    traceFlags = "01",
    traceState = "vendor=value",
    name = "ISalesManager.listProducts()",
    kind = SpanKind.CLIENT,
    startTimeUnixNano = 1_000_000_001,
    endTimeUnixNano = 1_000_000_101,
    attributes = mapOf(
        "rpc.system.name" to "ifx",
        "rpc.method" to "manager.sales.contract.ISalesManager/listProducts()",
    ),
)
