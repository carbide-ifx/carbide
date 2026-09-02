package ifx.telemetry.otel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BatchSpanProcessorTest {
    @Test
    fun `onEnd does not wait for a slow exporter`() = runBlocking {
        val exportStarted = CompletableDeferred<Unit>()
        val releaseExport = CompletableDeferred<Unit>()
        val exporter = SpanExporter {
            exportStarted.complete(Unit)
            releaseExport.await()
        }
        val processor = BatchSpanProcessor(
            exporter = exporter,
            maxQueueSize = 2,
            maxExportBatchSize = 1,
        )

        withTimeout(1.seconds) { processor.onEnd(testSpan("one")) }
        withTimeout(1.seconds) { exportStarted.await() }
        releaseExport.complete(Unit)
        processor.shutdown()
    }

    @Test
    fun `exports full and flushed partial batches`() = runBlocking {
        val exporter = RecordingBatchExporter()
        val processor = BatchSpanProcessor(
            exporter = exporter,
            maxQueueSize = 8,
            maxExportBatchSize = 3,
            scheduledDelay = 10.seconds,
        )

        processor.onEnd(testSpan("one"))
        processor.onEnd(testSpan("two"))
        processor.onEnd(testSpan("three"))
        withTimeout(1.seconds) { exporter.firstExport.await() }
        processor.onEnd(testSpan("four"))
        processor.flush()

        assertEquals(listOf(listOf("one", "two", "three"), listOf("four")), exporter.batches)
        processor.shutdown()
        assertEquals(true, exporter.shutDown)
    }

    @Test
    fun `exports a partial batch after the scheduled delay`() = runBlocking {
        val exporter = RecordingBatchExporter()
        val processor = BatchSpanProcessor(
            exporter = exporter,
            maxQueueSize = 4,
            maxExportBatchSize = 4,
            scheduledDelay = 20.milliseconds,
        )

        processor.onEnd(testSpan("one"))
        withTimeout(1.seconds) { exporter.firstExport.await() }

        assertEquals(listOf(listOf("one")), exporter.batches)
        processor.shutdown()
    }

    @Test
    fun `reports queue overflow without blocking the caller`() = runBlocking {
        val exportStarted = CompletableDeferred<Unit>()
        val releaseExport = CompletableDeferred<Unit>()
        val drops = mutableListOf<DroppedSpans>()
        val processor = BatchSpanProcessor(
            exporter = SpanExporter {
                exportStarted.complete(Unit)
                releaseExport.await()
            },
            maxQueueSize = 1,
            maxExportBatchSize = 1,
            onDroppedSpans = drops::add,
        )

        processor.onEnd(testSpan("exporting"))
        withTimeout(1.seconds) { exportStarted.await() }
        processor.onEnd(testSpan("queued"))
        processor.onEnd(testSpan("dropped"))

        assertEquals(listOf(SpanDropReason.QUEUE_FULL), drops.map(DroppedSpans::reason))
        releaseExport.complete(Unit)
        processor.shutdown()
    }

    @Test
    fun `times out export and does not retry`() = runBlocking {
        var attempts = 0
        val drops = mutableListOf<DroppedSpans>()
        val processor = BatchSpanProcessor(
            exporter = SpanExporter {
                attempts += 1
                delay(10.seconds)
            },
            maxQueueSize = 2,
            maxExportBatchSize = 1,
            exportTimeout = 20.milliseconds,
            onDroppedSpans = drops::add,
        )

        processor.onEnd(testSpan("one"))
        processor.flush()

        assertEquals(1, attempts)
        assertEquals(1, drops.single().count)
        assertEquals(SpanDropReason.EXPORT_TIMEOUT, drops.single().reason)
        processor.shutdown()
    }

    @Test
    fun `reports failed batches and does not retry`() = runBlocking {
        var attempts = 0
        val drops = mutableListOf<DroppedSpans>()
        val processor = BatchSpanProcessor(
            exporter = SpanExporter {
                attempts += 1
                error("collector unavailable")
            },
            maxQueueSize = 2,
            maxExportBatchSize = 2,
            scheduledDelay = 10.seconds,
            onDroppedSpans = drops::add,
        )

        processor.onEnd(testSpan("one"))
        processor.onEnd(testSpan("two"))
        processor.flush()

        assertEquals(1, attempts)
        assertEquals(2, drops.single().count)
        assertEquals(SpanDropReason.EXPORT_FAILURE, drops.single().reason)
        assertEquals("collector unavailable", drops.single().cause?.message)
        processor.shutdown()
    }

    @Test
    fun `shutdown drains the queue and rejects later spans`() = runBlocking {
        val exporter = RecordingBatchExporter()
        val drops = mutableListOf<DroppedSpans>()
        val processor = BatchSpanProcessor(
            exporter = exporter,
            maxQueueSize = 4,
            maxExportBatchSize = 4,
            scheduledDelay = 10.seconds,
            onDroppedSpans = drops::add,
        )

        processor.onEnd(testSpan("queued"))
        processor.shutdown()
        processor.onEnd(testSpan("late"))

        assertEquals(listOf(listOf("queued")), exporter.batches)
        assertEquals(SpanDropReason.PROCESSOR_SHUT_DOWN, drops.single().reason)
    }
}

private class RecordingBatchExporter : SpanExporter {
    val batches = mutableListOf<List<String>>()
    val firstExport = CompletableDeferred<Unit>()
    var shutDown = false

    override suspend fun export(span: FinishedSpan) {
        error("BatchSpanProcessor must use batch export")
    }

    override suspend fun export(spans: List<FinishedSpan>) {
        batches += spans.map(FinishedSpan::name)
        firstExport.complete(Unit)
    }

    override suspend fun shutdown() {
        shutDown = true
    }
}

private fun testSpan(name: String) = FinishedSpan(
    serviceName = "test-service",
    traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
    spanId = "00f067aa0ba902b7",
    parentSpanId = null,
    traceFlags = "01",
    name = name,
    kind = SpanKind.CLIENT,
    startTimeUnixNano = 1,
    endTimeUnixNano = 2,
    attributes = emptyMap(),
)
