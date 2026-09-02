@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package ifx.telemetry.otel.metric

import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.InteractionType
import ifx.telemetry.otel.TelemetryResource
import ifx.telemetry.otel.internal.unixTimeNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RpcCallMeasurement(
    val resource: TelemetryResource,
    val direction: CallDirection,
    val rpcMethod: String,
    val interactionType: InteractionType,
    val durationSeconds: Double,
    val errorType: String? = null,
)

fun interface RpcMetricRecorder {
    suspend fun record(measurement: RpcCallMeasurement)
}

fun interface MetricExporter {
    suspend fun export(metrics: List<HistogramMetric>)

    suspend fun shutdown() = Unit
}

data class HistogramMetric(
    val resource: TelemetryResource,
    val name: String,
    val unit: String,
    val attributes: Map<String, String>,
    val startTimeUnixNano: Long,
    val timeUnixNano: Long,
    val count: Long,
    val sum: Double,
    val min: Double,
    val max: Double,
    val explicitBounds: List<Double>,
    val bucketCounts: List<Long>,
)

class RpcMetrics(
    private val exporter: MetricExporter,
    private val exportInterval: Duration = 60.seconds,
    private val exportTimeout: Duration = 30.seconds,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    private val onExportFailure: suspend (Throwable) -> Unit = {},
) : RpcMetricRecorder {
    private data class HistogramKey(
        val resource: TelemetryResource,
        val name: String,
        val attributes: Map<String, String>,
    )

    private class HistogramState(
        val startTimeUnixNano: Long,
        val bucketCounts: LongArray,
        var count: Long = 0,
        var sum: Double = 0.0,
        var min: Double = Double.POSITIVE_INFINITY,
        var max: Double = Double.NEGATIVE_INFINITY,
    )

    private val acceptingMeasurements = AtomicBoolean(true)
    private val stateMutex = Mutex()
    private val exportMutex = Mutex()
    private val shutdownMutex = Mutex()
    private val histograms = mutableMapOf<HistogramKey, HistogramState>()
    private val periodicJob: Job

    init {
        require(exportInterval > Duration.ZERO) { "exportInterval must be greater than zero" }
        require(exportTimeout > Duration.ZERO) { "exportTimeout must be greater than zero" }

        periodicJob = CoroutineScope(coroutineContext + SupervisorJob()).launch {
            while (isActive) {
                delay(exportInterval)
                exportCurrent()
            }
        }
    }

    override suspend fun record(measurement: RpcCallMeasurement) {
        require(measurement.durationSeconds >= 0.0) { "durationSeconds must not be negative" }
        if (!acceptingMeasurements.load()) return

        val attributes = buildMap {
            put("rpc.system.name", "ifx")
            put("rpc.method", measurement.rpcMethod)
            put("ifx.interaction.type", measurement.interactionType.name.lowercase())
            measurement.errorType?.let { put("error.type", it) }
        }
        val key = HistogramKey(
            resource = measurement.resource,
            name = when (measurement.direction) {
                CallDirection.CLIENT -> "rpc.client.call.duration"
                CallDirection.SERVER -> "rpc.server.call.duration"
            },
            attributes = attributes,
        )

        stateMutex.lock()
        try {
            if (!acceptingMeasurements.load()) return
            val histogram = histograms.getOrPut(key) {
                HistogramState(
                    startTimeUnixNano = unixTimeNanos(),
                    bucketCounts = LongArray(RPC_DURATION_BOUNDS_SECONDS.size + 1),
                )
            }
            histogram.count += 1
            histogram.sum += measurement.durationSeconds
            histogram.min = minOf(histogram.min, measurement.durationSeconds)
            histogram.max = maxOf(histogram.max, measurement.durationSeconds)
            val bucket = RPC_DURATION_BOUNDS_SECONDS.indexOfFirst { measurement.durationSeconds <= it }
                .takeIf { it >= 0 }
                ?: RPC_DURATION_BOUNDS_SECONDS.size
            histogram.bucketCounts[bucket] += 1
        } finally {
            stateMutex.unlock()
        }
    }

    suspend fun flush() {
        shutdownMutex.lock()
        try {
            if (acceptingMeasurements.load()) exportCurrent()
        } finally {
            shutdownMutex.unlock()
        }
    }

    suspend fun shutdown() {
        shutdownMutex.lock()
        try {
            if (acceptingMeasurements.compareAndSet(expectedValue = true, newValue = false)) {
                periodicJob.cancelAndJoin()
                exportCurrent()
                exporter.shutdown()
            }
        } finally {
            shutdownMutex.unlock()
        }
    }

    private suspend fun exportCurrent() {
        exportMutex.lock()
        try {
            val metrics = snapshot()
            if (metrics.isEmpty()) return
            try {
                withTimeout(exportTimeout) { exporter.export(metrics) }
            } catch (timeout: TimeoutCancellationException) {
                reportFailure(timeout)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                reportFailure(failure)
            }
        } finally {
            exportMutex.unlock()
        }
    }

    private suspend fun snapshot(): List<HistogramMetric> {
        stateMutex.lock()
        try {
            val timeUnixNano = unixTimeNanos()
            return histograms.map { (key, state) ->
                HistogramMetric(
                    resource = key.resource,
                    name = key.name,
                    unit = "s",
                    attributes = key.attributes,
                    startTimeUnixNano = state.startTimeUnixNano,
                    timeUnixNano = timeUnixNano,
                    count = state.count,
                    sum = state.sum,
                    min = state.min,
                    max = state.max,
                    explicitBounds = RPC_DURATION_BOUNDS_SECONDS,
                    bucketCounts = state.bucketCounts.toList(),
                )
            }
        } finally {
            stateMutex.unlock()
        }
    }

    private suspend fun reportFailure(failure: Throwable) {
        try {
            onExportFailure(failure)
        } catch (_: Throwable) {
            // Telemetry diagnostics must not alter the instrumented application.
        }
    }
}

internal val RPC_DURATION_BOUNDS_SECONDS = listOf(
    0.005,
    0.01,
    0.025,
    0.05,
    0.075,
    0.1,
    0.25,
    0.5,
    0.75,
    1.0,
    2.5,
    5.0,
    7.5,
    10.0,
)
