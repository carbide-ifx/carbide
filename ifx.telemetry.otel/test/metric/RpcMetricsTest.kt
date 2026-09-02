package ifx.telemetry.otel.metric

import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.InteractionType
import ifx.telemetry.otel.TelemetryResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RpcMetricsTest {
    @Test
    fun `aggregates client and server duration histograms in seconds`() = runBlocking {
        val exporter = RecordingMetricExporter()
        val metrics = RpcMetrics(exporter, exportInterval = 10.seconds)
        val resource = TelemetryResource("sales-service")

        metrics.record(measurement(resource, CallDirection.CLIENT, durationSeconds = 0.004))
        metrics.record(measurement(resource, CallDirection.CLIENT, durationSeconds = 0.006))
        metrics.record(
            measurement(
                resource,
                CallDirection.SERVER,
                durationSeconds = 0.02,
                errorType = "test.Failure",
            ),
        )
        metrics.flush()

        val exported = exporter.exports.single()
        val client = exported.single { it.name == "rpc.client.call.duration" }
        val server = exported.single { it.name == "rpc.server.call.duration" }
        assertEquals("s", client.unit)
        assertEquals(2, client.count)
        assertEquals(0.01, client.sum)
        assertEquals(1, client.bucketCounts[0])
        assertEquals(1, client.bucketCounts[1])
        assertEquals("manager.sales.contract.ISalesManager/listProducts()", client.attributes["rpc.method"])
        assertEquals("request_response", client.attributes["ifx.interaction.type"])
        assertEquals("test.Failure", server.attributes["error.type"])
        metrics.shutdown()
    }

    @Test
    fun `periodic export happens outside the recording call`() = runBlocking {
        val exportStarted = CompletableDeferred<Unit>()
        val releaseExport = CompletableDeferred<Unit>()
        val metrics = RpcMetrics(
            exporter = MetricExporter {
                exportStarted.complete(Unit)
                releaseExport.await()
            },
            exportInterval = 20.milliseconds,
        )

        withTimeout(1.seconds) {
            metrics.record(measurement(TelemetryResource("sales-service"), CallDirection.CLIENT, 0.01))
        }
        withTimeout(1.seconds) { exportStarted.await() }
        releaseExport.complete(Unit)
        metrics.shutdown()
    }

    @Test
    fun `export timeout is reported without retrying`() = runBlocking {
        var attempts = 0
        val failures = mutableListOf<Throwable>()
        val metrics = RpcMetrics(
            exporter = MetricExporter {
                attempts += 1
                delay(10.seconds)
            },
            exportInterval = 10.seconds,
            exportTimeout = 20.milliseconds,
            onExportFailure = failures::add,
        )

        metrics.record(measurement(TelemetryResource("sales-service"), CallDirection.CLIENT, 0.01))
        metrics.flush()

        assertEquals(1, attempts)
        assertEquals(1, failures.size)
        metrics.shutdown()
    }

    @Test
    fun `shutdown exports current values and closes the exporter`() = runBlocking {
        val exporter = RecordingMetricExporter()
        val metrics = RpcMetrics(exporter, exportInterval = 10.seconds)

        metrics.record(measurement(TelemetryResource("sales-service"), CallDirection.SERVER, 0.01))
        metrics.shutdown()
        metrics.record(measurement(TelemetryResource("sales-service"), CallDirection.SERVER, 0.02))

        assertEquals(1, exporter.exports.single().single().count)
        assertTrue(exporter.shutDown)
    }
}

private class RecordingMetricExporter : MetricExporter {
    val exports = mutableListOf<List<HistogramMetric>>()
    var shutDown = false

    override suspend fun export(metrics: List<HistogramMetric>) {
        exports += metrics
    }

    override suspend fun shutdown() {
        shutDown = true
    }
}

private fun measurement(
    resource: TelemetryResource,
    direction: CallDirection,
    durationSeconds: Double,
    errorType: String? = null,
) = RpcCallMeasurement(
    resource = resource,
    direction = direction,
    rpcMethod = "manager.sales.contract.ISalesManager/listProducts()",
    interactionType = InteractionType.REQUEST_RESPONSE,
    durationSeconds = durationSeconds,
    errorType = errorType,
)
