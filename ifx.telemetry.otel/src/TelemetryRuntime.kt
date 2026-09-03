package ifx.telemetry.otel

import ifx.telemetry.otel.metric.RpcMetrics
import ifx.telemetry.otel.trace.ParentBasedSampler
import ifx.telemetry.otel.trace.Sampler
import ifx.telemetry.otel.trace.SpanProcessor
import ifx.telemetry.otel.trace.Tracer

/** Shared trace configuration and lifecycle for RPC, manual, and library instrumentation. */
class TelemetryRuntime(
    val resource: TelemetryResource,
    private val spanProcessor: SpanProcessor,
    sampler: Sampler = ParentBasedSampler(),
    private val rpcMetrics: RpcMetrics? = null,
    onObservabilityFailure: suspend (Throwable) -> Unit = {},
) {
    val tracer: Tracer = Tracer(
        spanProcessor = spanProcessor,
        resource = resource,
        sampler = sampler,
        onObservabilityFailure = onObservabilityFailure,
    )

    fun rpcInterceptor(logRpcCalls: Boolean = false): OpenTelemetryRpcInterceptor =
        OpenTelemetryRpcInterceptor(
            tracer = tracer,
            resource = resource,
            metricRecorder = rpcMetrics,
            logRpcCalls = logRpcCalls,
        )

    suspend fun flush() {
        try {
            tracer.flush()
        } finally {
            rpcMetrics?.flush()
        }
    }

    suspend fun shutdown() {
        try {
            rpcMetrics?.shutdown()
        } finally {
            tracer.shutdown()
        }
    }
}
