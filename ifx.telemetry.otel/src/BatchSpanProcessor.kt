@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package ifx.telemetry.otel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class SpanDropReason {
    QUEUE_FULL,
    PROCESSOR_SHUT_DOWN,
    EXPORT_FAILURE,
    EXPORT_TIMEOUT,
}

data class DroppedSpans(
    val count: Int,
    val reason: SpanDropReason,
    val cause: Throwable? = null,
)

class BatchSpanProcessor(
    private val exporter: SpanExporter,
    private val maxQueueSize: Int = 2_048,
    private val scheduledDelay: Duration = 5.seconds,
    private val maxExportBatchSize: Int = 512,
    private val exportTimeout: Duration = 30.seconds,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    private val onDroppedSpans: (DroppedSpans) -> Unit = {},
) : SpanProcessor {
    private sealed interface Command {
        data class Span(val value: FinishedSpan) : Command
        data class Flush(val completion: CompletableDeferred<Unit>) : Command
    }

    private data class Received(val result: ChannelResult<Command>)

    private val acceptingSpans = AtomicBoolean(true)
    private val queue: Channel<Command>
    private val worker: Job

    init {
        require(maxQueueSize > 0) { "maxQueueSize must be greater than zero" }
        require(maxExportBatchSize in 1..maxQueueSize) {
            "maxExportBatchSize must be between one and maxQueueSize"
        }
        require(scheduledDelay > Duration.ZERO) { "scheduledDelay must be greater than zero" }
        require(exportTimeout > Duration.ZERO) { "exportTimeout must be greater than zero" }

        queue = Channel(maxQueueSize)
        worker = CoroutineScope(coroutineContext + SupervisorJob()).launch { processQueue() }
    }

    override suspend fun onEnd(span: FinishedSpan) {
        if (!acceptingSpans.load()) {
            reportDrop(DroppedSpans(1, SpanDropReason.PROCESSOR_SHUT_DOWN))
            return
        }

        if (queue.trySend(Command.Span(span)).isFailure) {
            val reason = if (acceptingSpans.load()) {
                SpanDropReason.QUEUE_FULL
            } else {
                SpanDropReason.PROCESSOR_SHUT_DOWN
            }
            reportDrop(DroppedSpans(1, reason))
        }
    }

    override suspend fun flush() {
        if (!acceptingSpans.load()) {
            worker.join()
            return
        }

        val completion = CompletableDeferred<Unit>()
        val result = queue.trySend(Command.Flush(completion))
        if (result.isFailure) {
            try {
                queue.send(Command.Flush(completion))
            } catch (_: ClosedSendChannelException) {
                worker.join()
                return
            }
        }
        completion.await()
    }

    override suspend fun shutdown() {
        if (acceptingSpans.compareAndSet(expectedValue = true, newValue = false)) {
            queue.close()
        }
        worker.join()
    }

    private suspend fun processQueue() {
        val batch = ArrayList<FinishedSpan>(maxExportBatchSize)
        var batchStartedAt: TimeMark? = null

        try {
            while (true) {
                val received = if (batch.isEmpty()) {
                    Received(queue.receiveCatching())
                } else {
                    val remaining = scheduledDelay - requireNotNull(batchStartedAt).elapsedNow()
                    if (remaining <= Duration.ZERO) null else {
                        withTimeoutOrNull(remaining) { Received(queue.receiveCatching()) }
                    }
                }

                if (received == null) {
                    export(batch)
                    batchStartedAt = null
                    continue
                }

                val command = received.result.getOrNull()
                if (command == null) {
                    export(batch)
                    break
                }

                when (command) {
                    is Command.Span -> {
                        if (batch.isEmpty()) batchStartedAt = TimeSource.Monotonic.markNow()
                        batch += command.value
                        if (batch.size == maxExportBatchSize) {
                            export(batch)
                            batchStartedAt = null
                        }
                    }

                    is Command.Flush -> {
                        export(batch)
                        batchStartedAt = null
                        command.completion.complete(Unit)
                    }
                }
            }
        } finally {
            exporter.shutdown()
        }
    }

    private suspend fun export(batch: MutableList<FinishedSpan>) {
        if (batch.isEmpty()) return

        val spans = batch.toList()
        batch.clear()
        try {
            withTimeout(exportTimeout) { exporter.export(spans) }
        } catch (timeout: TimeoutCancellationException) {
            reportDrop(DroppedSpans(spans.size, SpanDropReason.EXPORT_TIMEOUT, timeout))
        } catch (failure: Throwable) {
            reportDrop(DroppedSpans(spans.size, SpanDropReason.EXPORT_FAILURE, failure))
        }
    }

    private fun reportDrop(droppedSpans: DroppedSpans) {
        try {
            onDroppedSpans(droppedSpans)
        } catch (_: Throwable) {
            // Telemetry diagnostics must not alter the instrumented application.
        }
    }
}
