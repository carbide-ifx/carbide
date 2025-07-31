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
import ifx.protocol.contract.ProtocolException
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class KoInvocationTest : FreeSpec({

    val protocol = RSocketProtocol()
    val fireAndForgetService = FireAndForget()
    val requestResponseService = RequestResponse()
    val requestStreamService = RequestStream()
    val host = Host(protocol)
//    .addInterceptors(LoggingInterceptor("Server: "), Rot13Interceptor())
        .registerService<IFireAndForget> { fireAndForgetService }
        .registerService<IRequestResponse> { requestResponseService }
        .registerService<IRequestStream> { requestStreamService }
        .open()
    val proxyFactory: IProxyFactory = ProxyFactory(protocol)
//    .addInterceptors(Rot13Interceptor(), LoggingInterceptor("Proxy: "))

    "Client exception - Server not found" {
        shouldThrow<ProtocolException> {
            proxyFactory.create<INonExsiting>()
        }
    }


    val fireAndForgetProxy = proxyFactory.create<IFireAndForget>()

    "Fire and forget" {
        runTest {
            fireAndForgetProxy.fireAndForget()
            fireAndForgetProxy.blockingFireAndForget()
            eventually(100.milliseconds) {
                fireAndForgetService.fireAndForgetCalled shouldBe true
                fireAndForgetService.blockingFireAndForgetCalled shouldBe true
            }
        }
    }

    "Fire and forget with parameter" {
        fireAndForgetProxy.fireAndForgetParam("Hello")
        fireAndForgetProxy.blockingFireAndForgetParam("Hello")
        eventually(100.milliseconds) {
            fireAndForgetService.fireAndForgetParamCalled shouldBe "Hello"
            fireAndForgetService.blockingFireAndForgetParamCalled shouldBe "Hello"
        }
    }

    "Fire and forget does not cause exception in proxy" {
        proxyFactory.create<IFireAndForget>().fireAndForgetWithException()
    }
    "Blocking Fire and forget does not cause exception in proxy" {
        proxyFactory.create<IFireAndForget>().blockingFireAndForgetWithException()
    }


    // RequestResponse tests
    val requestResponseProxy = proxyFactory.create<IRequestResponse>()

    "Zero parameters" {
        requestResponseProxy.hello() shouldBe "Hello"
        requestResponseProxy.blockingHello() shouldBe "Hello"
        requestResponseProxy.list() shouldBe listOf(1, 2, 3)
        requestResponseProxy.blockingList() shouldBe listOf(1, 2, 3)

    }

    "Service exception" {
        shouldThrow<ProtocolException> { requestResponseProxy.exception() }.let { println(it) }
        shouldThrow<ProtocolException> { requestResponseProxy.blockingException() }.let { println(it) }
    }

    "Direct invocation" {
        requestResponseProxy.add(IntPair(1, 1)) shouldBe 2
        requestResponseProxy.blockingAdd(IntPair(1, 1)) shouldBe 2
    }

    "Overloads support" {
        requestResponseProxy.add(IntPair(1, 1)) shouldBe 2
        requestResponseProxy.add(FloatPair(1.5f, 1.5f)) shouldBe 3
    }


    "Polymorphism support" {
        requestResponseProxy.polymorphicSquare(IntPair(5, 5)) shouldBe IntPair(25, 25)
        requestResponseProxy.polymorphicSquare(IntPair(5, 5)) shouldBe IntPair(25, 25)
        requestResponseProxy.polymorphicDefault(IntPair(5, 5)) shouldBe IntPair(25, 25)
    }

    // RequestStream tests

    val requestStreamProxy = proxyFactory.create<IRequestStream>()

    "Request stream" {
        requestStreamProxy.stream().toList() shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6))
        requestStreamProxy.blockingStream().toList() shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6))
    }

    "Request stream with parameters" {
        requestStreamProxy.streamWithParams(5).toList() shouldBe listOf(0, 1, 2, 3, 4)
        requestStreamProxy.blockingStreamWithParams(5).toList() shouldBe listOf(0, 1, 2, 3, 4)
    }

    "exceptions" {
        shouldThrow<ProtocolException> { requestStreamProxy.streamWithException().toList() }.let { println(it) }
        shouldThrow<ProtocolException> { requestStreamProxy.blockingStreamWithException().toList() }.let { println(it) }
    }
})
