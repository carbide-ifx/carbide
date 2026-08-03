package ifx.protocol.contract

import ifx.context.Context
import ifx.context.getOrNull
import ifx.context.set
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class InterceptorPipelineTest {
    @Test
    fun `client interceptors surround the complete stream in registration order`() = runBlocking {
        val events = mutableListOf<String>()
        val pipeline = ClientInterceptorPipeline(
            service = "test.Service",
            interceptors = listOf(RecordingInterceptor("first", events), RecordingInterceptor("second", events)),
            nextBinding = RecordingBinding(events),
        )

        val responses = pipeline.requestStream("stream", Message("{}", "request")).toList()

        assertEquals(listOf("one", "two"), responses.map(Message::body))
        assertEquals(
            listOf(
                "first before",
                "second before",
                "binding before",
                "binding after",
                "second after",
                "first after",
            ),
            events,
        )
    }

    @Test
    fun `server interceptors mirror client order`() = runBlocking {
        val events = mutableListOf<String>()
        val pipeline = ServerInterceptorPipeline(
            service = "test.Service",
            interceptors = listOf(RecordingInterceptor("first", events), RecordingInterceptor("second", events)),
            nextBinding = RecordingBinding(events),
        )

        pipeline.requestResponse("response", Message("{}", "request"))

        assertEquals(
            listOf(
                "second before",
                "first before",
                "binding before",
                "binding after",
                "first after",
                "second after",
            ),
            events,
        )
    }

    @Test
    fun `pipeline exposes the qualified service name to interceptors`() = runBlocking {
        var observedService: String? = null
        val inspector = IInterceptor { call, next ->
            flow {
                observedService = call.service
                emitAll(next(call))
            }
        }
        val binding = object : EmptyBinding() {
            override suspend fun requestResponse(operation: String, message: Message): Message =
                Message("{}", "response")
        }
        val pipeline = ClientInterceptorPipeline(
            service = "manager.sales.contract.ISalesManager",
            interceptors = listOf(inspector),
            nextBinding = binding,
        )

        pipeline.requestResponse("listProducts()", Message("{}", "request"))

        assertEquals("manager.sales.contract.ISalesManager", observedService)
    }

    @Test
    fun `client context is injected into its reserved header`() = runBlocking {
        val expected = Caller("client-user")
        val contextInterceptor = ContextInterceptor()
        val observed = mutableListOf<Pair<Caller?, Caller?>>()
        val inspector = IInterceptor { call, next ->
            flow {
                observed += Context.current().getOrNull<Caller>() to
                    call.message.context().getOrNull<Caller>()
                emitAll(next(call))
            }
        }
        val binding = object : EmptyBinding() {
            override suspend fun requestResponse(operation: String, message: Message): Message {
                observed += Context.current().getOrNull<Caller>() to
                    message.context().getOrNull<Caller>()
                return Message("{}", "response")
            }
        }
        val pipeline = ClientInterceptorPipeline("test.Service", listOf(contextInterceptor, inspector), binding)

        withContext(Context().set(expected)) {
            pipeline.requestResponse("response", Message("{}", "request"))
        }

        val expectedObservations: List<Pair<Caller?, Caller?>> = listOf(
            expected to expected,
            expected to expected,
        )
        assertEquals(
            expectedObservations,
            observed,
        )
    }

    @Test
    fun `server context types remain active while stream is collected`() = runBlocking {
        val expectedCaller = Caller("server-user")
        val expectedRequest = RequestMetadata("request-42")
        val contextInterceptor = ContextInterceptor()
        val observed = mutableListOf<Pair<Caller?, RequestMetadata?>>()
        val binding = object : EmptyBinding() {
            override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
                observed += Context.current().getOrNull<Caller>() to
                    Context.current().getOrNull<RequestMetadata>()
                emit(Message("{}", "response"))
            }
        }
        val pipeline = ServerInterceptorPipeline(
            "test.Service",
            interceptors = listOf(contextInterceptor),
            nextBinding = binding,
        )
        val message = Message("{}", "request")
            .withContext(Context().set(expectedCaller).set(expectedRequest))

        pipeline.requestStream("stream", message).toList()

        val expectedObservations: List<Pair<Caller?, RequestMetadata?>> =
            listOf(expectedCaller to expectedRequest)
        assertEquals(expectedObservations, observed)
    }

    @Test
    fun `serializable context types propagate without registration`() = runBlocking {
        val expected = RequestMetadata("automatic")
        val contextInterceptor = ContextInterceptor()
        val binding = object : EmptyBinding() {
            override suspend fun requestResponse(operation: String, message: Message): Message {
                assertEquals(expected, message.context().getOrNull<RequestMetadata>())
                return Message("{}", "response")
            }
        }
        val pipeline = ClientInterceptorPipeline("test.Service", listOf(contextInterceptor), binding)

        val response = withContext(Context().set(expected)) {
            pipeline.requestResponse("response", Message("{}", "request"))
        }
        assertEquals("response", response.body)
    }

    @Test
    fun `unknown context elements survive serialization`() {
        val rawContext = JsonObject(mapOf("future.context" to JsonPrimitive("opaque")))
        val incoming = Message("{}", "request")
            .withHeader(Context.HEADER_KEY, rawContext)

        val outgoing = Message("{}", "request").withContext(incoming.context())

        assertEquals(rawContext, outgoing.headers()[Context.HEADER_KEY])
    }

    @Test
    fun `headers support string values for future propagation formats`() {
        val message = Message("{}", "body")
            .withHeader("traceparent", JsonPrimitive("00-trace-span-01"))

        assertEquals(JsonPrimitive("00-trace-span-01"), message.headers()["traceparent"])
    }
}

@Serializable
@SerialName("ifx.caller")
private data class Caller(val name: String)

@Serializable
@SerialName("ifx.request")
private data class RequestMetadata(val requestId: String)

private class RecordingInterceptor(
    private val name: String,
    private val events: MutableList<String>,
) : IInterceptor {
    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        events += "$name before"
        try {
            emitAll(next(call))
        } finally {
            events += "$name after"
        }
    }
}

private class RecordingBinding(private val events: MutableList<String>) : EmptyBinding() {
    override suspend fun requestResponse(operation: String, message: Message): Message {
        events += "binding before"
        return Message("{}", "response").also { events += "binding after" }
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
        events += "binding before"
        emit(Message("{}", "one"))
        emit(Message("{}", "two"))
        events += "binding after"
    }
}

private abstract class EmptyBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit

    override suspend fun requestResponse(operation: String, message: Message): Message =
        error("Unexpected request/response")

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
        error("Unexpected request stream")
}
