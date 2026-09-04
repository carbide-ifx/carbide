package ifx.telemetry.otel.ktor.client

import ifx.telemetry.otel.TelemetryResource
import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.SpanProcessor
import ifx.telemetry.otel.trace.Tracer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenTelemetryClientPluginTest {
    @Test
    fun `plugin creates a parented client span and injects trace context`() = runBlocking {
        val processor = RecordingHttpProcessor()
        val tracer = Tracer(processor, TelemetryResource("test-service"))
        var traceParent: String? = null
        val client = HttpClient(MockEngine { request ->
            traceParent = request.headers["traceparent"]
            respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf())
        }) {
            install(OpenTelemetryClientPlugin) {
                this.tracer = tracer
            }
        }

        try {
            tracer.span("parent") {
                client.get("https://inventory.example/products")
            }
        } finally {
            client.close()
        }

        val parent = processor.spans.single { it.name == "parent" }
        val http = processor.spans.single { it.kind == SpanKind.CLIENT }
        assertEquals(parent.traceId, http.traceId)
        assertEquals(parent.spanId, http.parentSpanId)
        assertEquals("GET", http.name)
        assertEquals("GET", http.attributes["http.request.method"])
        assertEquals("inventory.example", http.attributes["server.address"])
        assertEquals("503", http.attributes["http.response.status_code"])
        assertEquals("503", http.attributes["error.type"])
        val propagated = assertNotNull(traceParent).split('-')
        assertEquals(http.traceId, propagated[1])
        assertEquals(http.spanId, propagated[2])
    }

    @Test
    fun `filter can exclude requests`() = runBlocking {
        val processor = RecordingHttpProcessor()
        val tracer = Tracer(processor, TelemetryResource("test-service"))
        val client = HttpClient(MockEngine { respond("ok") }) {
            install(OpenTelemetryClientPlugin) {
                this.tracer = tracer
                shouldInstrument = { false }
            }
        }

        try {
            client.get("https://collector.example/v1/traces")
        } finally {
            client.close()
        }

        assertEquals(emptyList(), processor.spans)
    }
}

private class RecordingHttpProcessor : SpanProcessor {
    val spans = mutableListOf<FinishedSpan>()

    override suspend fun onEnd(span: FinishedSpan) {
        spans += span
    }

    override suspend fun flush() = Unit

    override suspend fun shutdown() = Unit
}
