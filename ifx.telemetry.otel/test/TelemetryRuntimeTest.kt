package ifx.telemetry.otel

import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.SpanContext
import ifx.telemetry.otel.trace.SpanProcessor
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryRuntimeTest {
    @Test
    fun `runtime shares its tracer with the RPC interceptor and owns processor lifecycle`() = runBlocking {
        val processor = LifecycleProcessor()
        val runtime = TelemetryRuntime(
            resource = TelemetryResource("test-service"),
            spanProcessor = processor,
        )

        runtime.tracer.span("manual") {
            runtime.rpcInterceptor().intercept(
                InterceptorCall(
                    direction = CallDirection.CLIENT,
                    service = "test.Service",
                    interactionType = InteractionType.REQUEST_RESPONSE,
                    operation = "call()",
                    message = Message("{}", "request"),
                ),
                InterceptorChain { flowOf(Message("{}", "response")) },
            ).toList()
        }
        runtime.flush()
        runtime.shutdown()

        val manual = processor.spans.single { it.name == "manual" }
        val rpc = processor.spans.single { it.name == "test.Service/call()" }
        assertEquals(manual.traceId, rpc.traceId)
        assertEquals(manual.spanId, rpc.parentSpanId)
        assertEquals(1, processor.flushes)
        assertEquals(1, processor.shutdowns)
    }

    @Test
    fun `runtime configures the tracer link limit`() = runBlocking {
        val processor = LifecycleProcessor()
        val runtime = TelemetryRuntime(
            resource = TelemetryResource("test-service"),
            spanProcessor = processor,
            maxLinksPerSpan = 0,
        )

        runtime.tracer.span("manual") {
            addLink(
                SpanContext(
                    traceId = "1".padStart(32, '0'),
                    spanId = "1".padStart(16, '0'),
                    traceFlags = "01",
                ),
            )
        }

        val span = processor.spans.single()
        assertEquals(emptyList(), span.links)
        assertEquals(1, span.droppedLinksCount)
    }
}

private class LifecycleProcessor : SpanProcessor {
    val spans = mutableListOf<FinishedSpan>()
    var flushes = 0
    var shutdowns = 0

    override suspend fun onEnd(span: FinishedSpan) {
        spans += span
    }

    override suspend fun flush() {
        flushes += 1
    }

    override suspend fun shutdown() {
        shutdowns += 1
    }
}
