package ifx.telemetry.otel

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import ifx.logging.Log
import ifx.logging.LogTag
import ifx.logging.LogTagCodec
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.protocol.contract.InterceptorPipeline
import ifx.protocol.contract.headers
import ifx.protocol.contract.withHeader
import ifx.logging.LogCorrelation
import ifx.logging.ServiceLogScope
import ifx.logging.installLogWriter
import ifx.telemetry.otel.metric.RpcCallMeasurement
import ifx.telemetry.otel.metric.RpcMetricRecorder
import ifx.telemetry.otel.trace.FinishedSpan
import ifx.telemetry.otel.trace.AlwaysOffSampler
import ifx.telemetry.otel.trace.ParentBasedSampler
import ifx.telemetry.otel.trace.Sampler
import ifx.telemetry.otel.trace.SpanExporter
import ifx.telemetry.otel.trace.SpanKind
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenTelemetryRpcInterceptorTest {
    @Test
    fun `client and server pipelines create parented spans in one trace`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(exporter, serviceName = "test-system")
        val serviceBinding = object : IBinding {
            override suspend fun fireAndForget(operation: String, message: Message) = Unit

            override suspend fun requestResponse(operation: String, message: Message): Message =
                Message("{}", "response")

            override fun requestStream(operation: String, message: Message): Flow<Message> = flowOf()
        }
        val server = InterceptorPipeline(
            service = "manager.sales.contract.ISalesManager",
            direction = CallDirection.SERVER,
            interceptors = listOf(interceptor),
            nextBinding = serviceBinding,
        )
        val client = InterceptorPipeline(
            service = "manager.sales.contract.ISalesManager",
            direction = CallDirection.CLIENT,
            interceptors = listOf(interceptor),
            nextBinding = server,
        )

        client.requestResponse("listProducts()", Message("{}", "request"))

        val serverSpan = exporter.spans.single { it.kind == SpanKind.SERVER }
        val clientSpan = exporter.spans.single { it.kind == SpanKind.CLIENT }
        assertEquals(clientSpan.traceId, serverSpan.traceId)
        assertEquals(clientSpan.spanId, serverSpan.parentSpanId)
        assertEquals("test-system", clientSpan.serviceName)
        assertEquals("test-system", serverSpan.serviceName)
    }

    @Test
    fun `client span is propagated as traceparent`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(exporter, serviceName = "sales-client")
        var forwardedMessage: Message? = null
        val chain = InterceptorChain { call ->
            forwardedMessage = call.message
            flowOf(Message("{}", "response"))
        }

        interceptor.intercept(
            InterceptorCall(CallDirection.CLIENT,
                service = "manager.sales.contract.ISalesManager",
                interactionType = InteractionType.REQUEST_RESPONSE,
                operation = "listProducts()",
                message = Message("{}", "request"),
            ),
            chain,
        ).toList()

        val span = exporter.spans.single()
        val traceParent = assertNotNull(
            assertNotNull(forwardedMessage).headers()["traceparent"] as? JsonPrimitive,
        ).content.split('-')
        assertEquals(span.traceId, traceParent[1])
        assertEquals(span.spanId, traceParent[2])
        assertEquals("sales-client", span.serviceName)
        assertEquals("manager.sales.contract.ISalesManager/listProducts()", span.name)
        assertEquals(span.attributes["rpc.method"], span.name)
        assertEquals(SpanKind.CLIENT, span.kind)
        assertNull(span.parentSpanId)
    }

    @Test
    fun `finished span carries the configured resource`() = runBlocking {
        val exporter = RecordingExporter()
        var measurement: RpcCallMeasurement? = null
        val resource = TelemetryResource(
            serviceName = "sales-service",
            serviceNamespace = "commerce",
            serviceVersion = "2.1.0",
            serviceInstanceId = "sales-7f9d",
            deploymentEnvironmentName = "staging",
            attributes = mapOf("cloud.region" to "eu-west-1"),
        )
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            resource = resource,
            metricRecorder = RpcMetricRecorder { measurement = it },
        )

        interceptor.intercept(
            InterceptorCall(CallDirection.SERVER,
                service = "manager.sales.contract.ISalesManager",
                interactionType = InteractionType.REQUEST_RESPONSE,
                operation = "listProducts()",
                message = Message("{}", "request"),
            ),
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        val span = exporter.spans.single()
        assertEquals(resource, span.resource)
        assertTrue(span.endTimeUnixNano >= span.startTimeUnixNano)
        assertTrue(requireNotNull(measurement).durationSeconds >= 0.0)
    }

    @Test
    fun `server span continues the remote trace`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(exporter, serviceName = "sales-service")
        val remoteTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val remoteSpanId = "00f067aa0ba902b7"
        val message = Message("{}", "request").withHeader(
            "TraceParent",
            JsonPrimitive("00-$remoteTraceId-$remoteSpanId-01"),
        ).withHeader("TraceState", JsonPrimitive("vendor=value"))

        interceptor.intercept(
            InterceptorCall(CallDirection.SERVER,
                service = "manager.sales.contract.ISalesManager",
                interactionType = InteractionType.REQUEST_RESPONSE,
                operation = "listProducts()",
                message = message,
            ),
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        val span = exporter.spans.single()
        assertEquals(remoteTraceId, span.traceId)
        assertEquals(remoteSpanId, span.parentSpanId)
        assertEquals("vendor=value", span.traceState)
        assertEquals("sales-service", span.serviceName)
        assertEquals(SpanKind.SERVER, span.kind)
    }

    @Test
    fun `span covers stream failure and records the error`() = runBlocking {
        val exporter = RecordingExporter()
        var measurement: RpcCallMeasurement? = null
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "stream-client",
            metricRecorder = RpcMetricRecorder { measurement = it },
        )
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.StreamService",
            interactionType = InteractionType.REQUEST_STREAM,
            operation = "stream()",
            message = Message("{}", "request"),
        )

        var thrown: Throwable? = null
        try {
            interceptor.intercept(call, InterceptorChain {
                flow {
                    emit(Message("{}", "first"))
                    error("stream failed")
                }
            }).toList()
        } catch (throwable: Throwable) {
            thrown = throwable
        }

        assertEquals("stream failed", thrown?.message)
        val span = exporter.spans.single()
        assertEquals("stream failed", span.error?.message)
        assertEquals(true, span.attributes["error.type"]?.endsWith("IllegalStateException"))
        assertEquals("request_stream", span.attributes["ifx.interaction.type"])
        assertEquals(span.attributes["error.type"], measurement?.errorType)
    }

    @Test
    fun `invalid traceparent starts a new trace`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(exporter, serviceName = "notification-service")
        val call = InterceptorCall(CallDirection.SERVER,
            service = "test.Service",
            interactionType = InteractionType.FIRE_AND_FORGET,
            operation = "notify()",
            message = Message("{}", "").withHeader("traceparent", JsonPrimitive("invalid")),
        )

        interceptor.intercept(call, InterceptorChain { flowOf() }).toList()

        val span = exporter.spans.single()
        assertEquals(32, span.traceId.length)
        assertEquals(16, span.spanId.length)
        assertNull(span.parentSpanId)
    }

    @Test
    fun `unsampled calls propagate but are not exported`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "test-client",
            sampler = ParentBasedSampler(AlwaysOffSampler),
        )
        var forwardedMessage: Message? = null
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request"),
        )

        interceptor.intercept(call, InterceptorChain {
            forwardedMessage = it.message
            flowOf(Message("{}", "response"))
        }).toList()

        assertEquals(emptyList(), exporter.spans)
        val traceParent = assertNotNull(
            assertNotNull(forwardedMessage).headers()["traceparent"] as? JsonPrimitive,
        ).content
        assertEquals("00", traceParent.substringAfterLast('-'))
    }

    @Test
    fun `RPC duration is recorded independently of trace sampling`() = runBlocking {
        val exporter = RecordingExporter()
        var measurement: RpcCallMeasurement? = null
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            resource = TelemetryResource("test-client"),
            sampler = ParentBasedSampler(AlwaysOffSampler),
            metricRecorder = RpcMetricRecorder { measurement = it },
        )
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request"),
        )

        interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        assertEquals(emptyList(), exporter.spans)
        assertEquals("test.Service/call()", measurement?.rpcMethod)
        assertEquals(CallDirection.CLIENT, measurement?.direction)
        assertEquals("test-client", measurement?.resource?.serviceName)
        assertEquals(null, measurement?.errorType)
        assertEquals(true, requireNotNull(measurement).durationSeconds >= 0.0)
    }

    @Test
    fun `parent based sampler preserves sampled upstream decision`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "test-server",
            sampler = ParentBasedSampler(AlwaysOffSampler),
        )
        val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val parentSpanId = "00f067aa0ba902b7"
        val call = InterceptorCall(CallDirection.SERVER,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request").withHeader(
                "traceparent",
                JsonPrimitive("00-$traceId-$parentSpanId-01"),
            ),
        )

        interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        val span = exporter.spans.single()
        assertEquals(traceId, span.traceId)
        assertEquals(parentSpanId, span.parentSpanId)
        assertEquals("01", span.traceFlags)
    }

    @Test
    fun `parent based sampler preserves unsampled upstream decision`() = runBlocking {
        val exporter = RecordingExporter()
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "test-server",
            sampler = ParentBasedSampler(),
        )
        val call = InterceptorCall(CallDirection.SERVER,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request").withHeader(
                "traceparent",
                JsonPrimitive("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"),
            ),
        )

        interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        assertEquals(emptyList(), exporter.spans)
    }

    @Test
    fun `export failures do not fail the RPC`() = runBlocking {
        var exportFailure: Throwable? = null
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = SpanExporter { error("collector unavailable") },
            serviceName = "test-client",
            onObservabilityFailure = {
                exportFailure = it
                error("diagnostic callback failed")
            },
        )
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request"),
        )

        val responses = interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        assertEquals(listOf("response"), responses.map(Message::body))
        assertEquals("collector unavailable", exportFailure?.message)
    }

    @Test
    fun `sampler failures do not fail the RPC`() = runBlocking {
        val exporter = RecordingExporter()
        var samplingFailure: Throwable? = null
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "test-client",
            sampler = Sampler { error("sampler failed") },
            onObservabilityFailure = { samplingFailure = it },
        )
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request"),
        )

        val responses = interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        assertEquals(listOf("response"), responses.map(Message::body))
        assertEquals(emptyList(), exporter.spans)
        assertEquals("sampler failed", samplingFailure?.message)
    }

    @Test
    fun `RPC logs use the active span correlation`() = runBlocking {
        val exporter = RecordingExporter()
        val logWriter = CorrelationLogWriter()
        installLogWriter(logWriter)
        var downstreamCorrelation: LogCorrelation? = null
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = exporter,
            serviceName = "test-client",
            logRpcCalls = true,
        )
        val call = InterceptorCall(CallDirection.CLIENT,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message("{}", "request"),
        )

        withContext(
            ServiceLogScope(
                serviceInterface = "engine.pricing.contract.IPricingEngine",
                serviceClassName = "engine.pricing.service.PricingEngine",
            ),
        ) {
            interceptor.intercept(
                call,
                InterceptorChain {
                    flow {
                        downstreamCorrelation = LogCorrelation.currentOrNull()
                        Log("Application").info { "Loading products" }
                        emit(Message("{}", "response"))
                    }
                },
            ).toList()
        }

        val span = exporter.spans.single()
        val expected = LogCorrelation(span.traceId, span.spanId, span.traceFlags)
        assertEquals(expected, downstreamCorrelation)
        assertEquals(listOf(expected, expected, expected), logWriter.entries.map { it.correlation })
        assertEquals(true, logWriter.entries[0].message.startsWith("IPricingEngine -> Service.call(): "))
        assertEquals(true, logWriter.entries[0].message.contains("traceparent"))
        assertEquals(false, logWriter.entries[0].display)
        assertEquals(false, logWriter.entries[0].retained)
        assertEquals("Loading products", logWriter.entries[1].message)
        assertEquals(true, logWriter.entries[1].retained)
        assertEquals(true, logWriter.entries[2].message.startsWith("IPricingEngine <- Service.call(): "))
    }

    @Test
    fun `server RPC logs distinguish receive from send`() = runBlocking {
        val logWriter = CorrelationLogWriter()
        installLogWriter(logWriter)
        val interceptor = OpenTelemetryRpcInterceptor(
            exporter = RecordingExporter(),
            serviceName = "test-server",
            logRpcCalls = true,
        )
        val call = InterceptorCall(CallDirection.SERVER,
            service = "test.Service",
            interactionType = InteractionType.REQUEST_RESPONSE,
            operation = "call()",
            message = Message(
                """{"traceparent":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"}""",
                "request",
            ),
        )

        interceptor.intercept(
            call,
            InterceptorChain { flowOf(Message("{}", "response")) },
        ).toList()

        assertEquals(true, logWriter.entries[0].message.startsWith("-> Service.call(): "))
        assertEquals(true, logWriter.entries[1].message.startsWith("Service.call() -> Message"))
    }
}

private class RecordingExporter : SpanExporter {
    val spans = mutableListOf<FinishedSpan>()

    override suspend fun export(span: FinishedSpan) {
        spans += span
    }
}

private class CorrelationLogWriter : LogWriter() {
    val entries = mutableListOf<CorrelatedLogEntry>()

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        LogTagCodec.decodeOrNull(tag)?.traceId != null

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val structuredTag = requireNotNull(LogTagCodec.decodeOrNull(tag))
        entries += CorrelatedLogEntry(
            correlation = structuredTag.toCorrelation(),
            message = message,
            display = structuredTag.display,
            retained = structuredTag.retained,
        )
    }
}

private data class CorrelatedLogEntry(
    val correlation: LogCorrelation,
    val message: String,
    val display: Boolean,
    val retained: Boolean,
)

private fun LogTag.toCorrelation(): LogCorrelation = LogCorrelation(
    traceId = requireNotNull(traceId),
    spanId = requireNotNull(spanId),
    traceFlags = requireNotNull(traceFlags),
)
