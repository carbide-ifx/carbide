package ifx.protocol.contract

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class UnhandledExceptionInterceptorTest {
    @Test
    fun `reports an exception escaping a request response and rethrows it`() = runBlocking {
        val failure = IllegalStateException("service failed")
        var reportedCall: InterceptorCall? = null
        var reportedException: Throwable? = null
        val pipeline = pipeline(
            interceptor = UnhandledExceptionInterceptor { call, exception ->
                reportedCall = call
                reportedException = exception
            },
            binding = object : ExceptionTestBinding() {
                override suspend fun requestResponse(operation: String, message: Message): Message = throw failure
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            pipeline.requestResponse("findProduct", Message("{}", "request"))
        }

        assertSame(failure, thrown)
        assertSame(failure, reportedException)
        assertEquals("test.IProductAccess", reportedCall?.service)
        assertEquals("findProduct", reportedCall?.operation)
        assertEquals(InteractionType.REQUEST_RESPONSE, reportedCall?.interactionType)
    }

    @Test
    fun `reports an exception thrown while collecting a stream`() = runBlocking {
        val failure = IllegalArgumentException("stream failed")
        val reported = mutableListOf<Throwable>()
        val pipeline = pipeline(
            interceptor = UnhandledExceptionInterceptor { _, exception -> reported += exception },
            binding = object : ExceptionTestBinding() {
                override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
                    emit(Message("{}", "first"))
                    throw failure
                }
            },
        )

        val thrown = assertFailsWith<IllegalArgumentException> {
            pipeline.requestStream("observeProducts", Message("{}", "request")).toList()
        }

        assertSame(failure, thrown)
        assertEquals(1, reported.size)
        assertSame(failure, reported.single())
    }

    @Test
    fun `does not report coroutine cancellation`() = runBlocking {
        val reported = mutableListOf<Throwable>()
        val pipeline = pipeline(
            interceptor = UnhandledExceptionInterceptor { _, exception -> reported += exception },
            binding = object : ExceptionTestBinding() {
                override suspend fun requestResponse(operation: String, message: Message): Message =
                    throw CancellationException("request cancelled")
            },
        )

        assertFailsWith<CancellationException> {
            pipeline.requestResponse("findProduct", Message("{}", "request"))
        }

        assertEquals(emptyList(), reported)
    }

    @Test
    fun `reporting failure does not replace the rpc failure`() = runBlocking {
        val failure = IllegalStateException("service failed")
        val pipeline = pipeline(
            interceptor = UnhandledExceptionInterceptor { _, _ -> error("reporting failed") },
            binding = object : ExceptionTestBinding() {
                override suspend fun requestResponse(operation: String, message: Message): Message = throw failure
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            pipeline.requestResponse("findProduct", Message("{}", "request"))
        }

        assertSame(failure, thrown)
    }

    private fun pipeline(
        interceptor: IInterceptor,
        binding: IBinding,
    ): ServerInterceptorPipeline = ServerInterceptorPipeline(
        service = "test.IProductAccess",
        interceptors = listOf(interceptor),
        nextBinding = binding,
    )
}

private abstract class ExceptionTestBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit

    override suspend fun requestResponse(operation: String, message: Message): Message =
        error("Unexpected request/response")

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
        error("Unexpected request stream")
}
