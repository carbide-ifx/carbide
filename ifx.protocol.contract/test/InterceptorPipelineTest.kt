package ifx.protocol.contract

import ifx.context.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class InterceptorPipelineTest {
    @Test
    fun `client interceptors surround the complete stream in registration order`() = runBlocking {
        val events = mutableListOf<String>()
        val pipeline = ClientInterceptorPipeline(
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
    fun `client context is injected into headers and coroutine context`() = runBlocking {
        val expected = Context(traceId = "client-trace")
        val observed = mutableListOf<Pair<String, String>>()
        val inspector = IInterceptor { call, next ->
            flow {
                observed += Context.current().traceId to call.message.context().traceId
                emitAll(next(call))
            }
        }
        val binding = object : EmptyBinding() {
            override suspend fun requestResponse(operation: String, message: Message): Message {
                observed += Context.current().traceId to message.context().traceId
                return Message("{}", "response")
            }
        }
        val pipeline = ClientInterceptorPipeline(listOf(inspector), binding)

        withContext(expected) {
            pipeline.requestResponse("response", Message("{}", "request"))
        }

        assertEquals(listOf("client-trace" to "client-trace", "client-trace" to "client-trace"), observed)
    }

    @Test
    fun `server context from headers remains active while stream is collected`() = runBlocking {
        val expected = Context(traceId = "server-trace")
        val observed = mutableListOf<String>()
        val binding = object : EmptyBinding() {
            override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
                observed += Context.current().traceId
                emit(Message("{}", "response"))
            }
        }
        val pipeline = ServerInterceptorPipeline(nextBinding = binding)

        pipeline.requestStream("stream", Message("{}", "request").withContext(expected)).toList()

        assertEquals(listOf("server-trace"), observed)
    }

    @Test
    fun `headers support string values for future propagation formats`() {
        val message = Message("{}", "body")
            .withHeader("traceparent", JsonPrimitive("00-trace-span-01"))

        assertEquals(JsonPrimitive("00-trace-span-01"), message.headers()["traceparent"])
    }
}

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
