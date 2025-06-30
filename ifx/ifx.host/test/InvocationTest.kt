package ifx.host

import ifx.host.IHost.Companion.registerService
import ifx.host.contract.FloatPair
import ifx.host.contract.IFireAndForget
import ifx.host.contract.INonExsiting
import ifx.host.contract.IRequestResponse
import ifx.host.contract.IRequestStream
import ifx.host.contract.IntPair
import ifx.host.service.FireAndForget
import ifx.host.service.RequestResponse
import ifx.host.service.RequestStream
import ifx.logging.Log
import ifx.protocol.contract.ProtocolException
import ifx.protocol.rsocket.RSocketEndpoint
import ifx.proxy.factory.ProxyFactory
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

val protocol = RSocketEndpoint()
val fireAndForgetService = FireAndForget()
val requestResponseService = RequestResponse()
val requestStreamService = RequestStream()
val host = Host()
    .addProtocol(protocol)
    .registerService<IFireAndForget> { fireAndForgetService }
    .registerService<IRequestResponse> { requestResponseService }
    .registerService<IRequestStream> { requestStreamService }
    .start()
val proxyFactory = ProxyFactory(protocol)

class InvocationTest() {

    @Test
    fun `Client exception - Server not found`() {
        shouldThrow<ProtocolException> {
            proxyFactory.create<INonExsiting>()
        }
    }


    val fireAndForgetProxy = proxyFactory.create<IFireAndForget>()

    @Test
    fun `Fire and forget`() {
        runTest {
            fireAndForgetProxy.fireAndForget()
            fireAndForgetProxy.blockingFireAndForget()
            eventually(100.milliseconds) {
                fireAndForgetService.fireAndForgetCalled shouldBe true
                fireAndForgetService.blockingFireAndForgetCalled shouldBe true
            }
        }
    }

    @Test
    fun `Fire and forget with parameter`() = runTest {
        fireAndForgetProxy.fireAndForgetParam("Hello")
        fireAndForgetProxy.blockingFireAndForgetParam("Hello")
        eventually(100.milliseconds) {
            fireAndForgetService.fireAndForgetParamCalled shouldBe "Hello"
            fireAndForgetService.blockingFireAndForgetParamCalled shouldBe "Hello"
        }
    }

// Todo: Exception should not be observable from the caller, but should be logged by  the host
//    @Test
//    fun `Fire and forget with exception`() = runTest {
//        shouldThrow<ProtocolException> {
//            proxyFactory.create<IFireAndForget>().fireAndForgetWithException()
//        }
//    }
//
//    @Test
//    fun `Blocking Fire and forget with exception`() = runTest {
//        shouldThrow<ProtocolException> {
//            proxyFactory.create<IFireAndForget>().blockingFireAndForgetWithException()
//        }
//    }


    // RequestResponse tests
    val requestResponseProxy = proxyFactory.create<IRequestResponse>()

    @Test
    fun `Zero parameters`() = runTest {
        requestResponseProxy.hello() shouldBe "Hello"
        requestResponseProxy.blockingHello() shouldBe "Hello"
        requestResponseProxy.list() shouldBe listOf(1, 2, 3)
        requestResponseProxy.blockingList() shouldBe listOf(1, 2, 3)

    }

    @Test
    fun `Service exception`() = runTest {
        shouldThrow<ProtocolException> { requestResponseProxy.exception() }
        shouldThrow<ProtocolException> { requestResponseProxy.blockingException() }
    }

    @Test
    fun `Direct invocation`() = runTest {
        requestResponseProxy.add(IntPair(1, 1)) shouldBe 2
        requestResponseProxy.blockingAdd(IntPair(1, 1)) shouldBe 2
    }

    @Test
    fun `Overloads support`() = runTest {
        requestResponseProxy.add(IntPair(1, 1)) shouldBe 2
        requestResponseProxy.add(FloatPair(1.5f, 1.5f)) shouldBe 3
    }


    @Test
    fun `Polymorphism support`() = runTest {
        requestResponseProxy.polymorphicSquare(IntPair(5, 5)) shouldBe IntPair(25, 25)
        requestResponseProxy.polymorphicSquare(IntPair(5, 5)) shouldBe IntPair(25, 25)
        requestResponseProxy.polymorphicDefault(IntPair(5, 5)) shouldBe IntPair(25, 25)
    }

    // RequestStream tests

    val requestStreamProxy = proxyFactory.create<IRequestStream>()

    @Test
    fun `Request stream`() = runTest {
        requestStreamProxy.stream().toList() shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6))
        requestStreamProxy.blockingStream().toList() shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6))
    }

    @Test
    fun `Request stream with parameters`() = runTest {
        requestStreamProxy.streamWithParams(5).toList() shouldBe listOf(0, 1, 2, 3, 4)
        requestStreamProxy.blockingStreamWithParams(5).toList() shouldBe listOf(0, 1, 2, 3, 4)
    }


    @Test
    fun `Handles over 1000 requests per second`() = runTest {
        val iterations = 10
        val myService = proxyFactory.create<IRequestResponse>()
        val duration = measureTime {
            repeat(iterations) {
                myService.add(IntPair(it, it)) shouldBe it * 2
            }
        }
        duration shouldBeLessThan 10.seconds
        println("Performed 10'000 requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS")
    }
}
