package ifx.telemetry.otel.export.otlp

import ifx.telemetry.otel.TelemetryResource
import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.SpanContext
import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.SpanLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
        assertContains(
            requestBody,
            "\"key\":\"telemetry.sdk.name\",\"value\":{\"stringValue\":\"opentelemetry\"}",
        )
        assertContains(
            requestBody,
            "\"key\":\"telemetry.sdk.version\",\"value\":{\"stringValue\":\"0.1.0\"}",
        )
        assertContains(requestBody, "\"scope\":{\"name\":\"ifx.telemetry.otel\",\"version\":\"0.1.0\"}")
        assertContains(requestBody, "\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"")
        assertContains(requestBody, "\"spanId\":\"00f067aa0ba902b7\"")
        assertContains(requestBody, "\"traceState\":\"vendor=value\"")
        assertContains(requestBody, "\"kind\":3")
        assertContains(requestBody, "\"startTimeUnixNano\":\"1000000001\"")
    }

    @Test
    fun `exporter emits complete resource identity and application attributes`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK)
        }
        val resource = TelemetryResource(
            serviceName = "sales-service",
            serviceNamespace = "commerce",
            serviceVersion = "2.1.0",
            serviceInstanceId = "sales-7f9d",
            deploymentEnvironmentName = "staging",
            attributes = mapOf("cloud.region" to "eu-west-1"),
        )
        val exporter = OtlpHttpSpanExporter(httpClient = HttpClient(engine))

        exporter.export(testSpan().copy(resource = resource))
        exporter.shutdown()

        assertContains(requestBody, "\"service.namespace\"")
        assertContains(requestBody, "\"stringValue\":\"commerce\"")
        assertContains(requestBody, "\"service.version\"")
        assertContains(requestBody, "\"stringValue\":\"2.1.0\"")
        assertContains(requestBody, "\"service.instance.id\"")
        assertContains(requestBody, "\"stringValue\":\"sales-7f9d\"")
        assertContains(requestBody, "\"deployment.environment.name\"")
        assertContains(requestBody, "\"stringValue\":\"staging\"")
        assertContains(requestBody, "\"cloud.region\"")
        assertContains(requestBody, "\"stringValue\":\"eu-west-1\"")
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

    @Test
    fun `batch export keeps different resources separate`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK)
        }
        val exporter = OtlpHttpSpanExporter(httpClient = HttpClient(engine))

        exporter.export(
            listOf(
                testSpan(),
                testSpan().copy(resource = TelemetryResource("inventory-service")),
            ),
        )
        exporter.shutdown()

        val resourceSpans = Json.parseToJsonElement(requestBody)
            .jsonObject.getValue("resourceSpans").jsonArray
        assertEquals(2, resourceSpans.size)
    }

    @Test
    fun `exporter emits span links and dropped link count`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.OK)
        }
        val exporter = OtlpHttpSpanExporter(httpClient = HttpClient(engine))
        val linkedContext = SpanContext(
            traceId = "5bf92f3577b34da6a3ce929d0e0e4736",
            spanId = "10f067aa0ba902b7",
            traceFlags = "01",
            traceState = "vendor=linked",
        )

        exporter.export(
            testSpan().copy(
                links = listOf(SpanLink(linkedContext, mapOf("messaging.operation.name" to "publish"))),
                droppedLinksCount = 2,
            ),
        )
        exporter.shutdown()

        assertContains(requestBody, "\"links\":[{\"traceId\":\"5bf92f3577b34da6a3ce929d0e0e4736\"")
        assertContains(requestBody, "\"spanId\":\"10f067aa0ba902b7\"")
        assertContains(requestBody, "\"traceState\":\"vendor=linked\"")
        assertContains(requestBody, "\"key\":\"messaging.operation.name\"")
        assertContains(requestBody, "\"flags\":1")
        assertContains(requestBody, "\"droppedLinksCount\":2")
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
