package ifx.telemetry.otel.trace

import ifx.logging.LogCorrelation
import ifx.telemetry.otel.TelemetryResource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TracerTest {
    @Test
    fun `manual spans parent nested work and expose log correlation`() = runBlocking {
        val processor = RecordingProcessor()
        val tracer = Tracer(processor, TelemetryResource("test-service"))
        var outerCorrelation: LogCorrelation? = null
        var innerCorrelation: LogCorrelation? = null

        tracer.span("outer", attributes = mapOf("initial" to "value")) {
            outerCorrelation = LogCorrelation.currentOrNull()
            setAttribute("result.count", 2)
            tracer.span("inner") {
                innerCorrelation = LogCorrelation.currentOrNull()
            }
        }

        val outer = processor.spans.single { it.name == "outer" }
        val inner = processor.spans.single { it.name == "inner" }
        assertEquals(outer.traceId, inner.traceId)
        assertEquals(outer.spanId, inner.parentSpanId)
        assertNotEquals(outer.spanId, inner.spanId)
        assertEquals(outer.spanId, outerCorrelation?.spanId)
        assertEquals(inner.spanId, innerCorrelation?.spanId)
        assertEquals("value", outer.attributes["initial"])
        assertEquals("2", outer.attributes["result.count"])
        assertNull(LogCorrelation.currentOrNull())
    }

    @Test
    fun `manual span records and rethrows failure`() = runBlocking {
        val processor = RecordingProcessor()
        val tracer = Tracer(processor, TelemetryResource("test-service"))

        val failure = assertFailsWith<IllegalStateException> {
            tracer.span("failing-work") { error("database unavailable") }
        }

        assertEquals("database unavailable", failure.message)
        val span = processor.spans.single()
        assertEquals(IllegalStateException::class.qualifiedName, span.error?.type)
        assertEquals("database unavailable", span.error?.message)
        assertEquals(IllegalStateException::class.qualifiedName, span.attributes["error.type"])
    }

    @Test
    fun `flow span covers lazy collection and restores context`() = runBlocking {
        val processor = RecordingProcessor()
        val tracer = Tracer(processor, TelemetryResource("test-service"))
        var collectionCorrelation: LogCorrelation? = null
        val values = flow {
            collectionCorrelation = LogCorrelation.currentOrNull()
            emit(1)
            emit(2)
        }.inSpan(tracer, "stream-work")

        assertEquals(emptyList(), processor.spans)
        assertEquals(listOf(1, 2), values.toList())
        val span = processor.spans.single()
        assertEquals(span.spanId, assertNotNull(collectionCorrelation).spanId)
        assertNull(LogCorrelation.currentOrNull())
    }
}

private class RecordingProcessor : SpanProcessor {
    val spans = mutableListOf<FinishedSpan>()

    override suspend fun onEnd(span: FinishedSpan) {
        spans += span
    }

    override suspend fun flush() = Unit

    override suspend fun shutdown() = Unit
}
