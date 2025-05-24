package ifx.rsocket.rsocket

import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.rsocket.InvocationException
import ifx.proxy.rsocket.ProxyFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.rsocket.kotlin.RSocketError
import kotlinx.coroutines.flow.toList


class RsocketServerTest : FreeSpec({
    val instance: IMyService = MyService()
    RSocketProtocol().bind(instance).start()
    "Invocations" - {
        val proxy = ProxyFactory.create<IMyService>()
        "Fire and forget" - {
            "fun hello()" {
                proxy.hello() shouldBe Unit
            }

            "fun blockingHello()" {
                proxy.blockingHello() shouldBe Unit
            }
        }

        "Request-Response" - {
            "fun add(pair: IntPair): Int" {
                proxy.add(IntPair(1, 1)) shouldBe 2
            }

            "fun add(pair: FloatPair): Float" {
                proxy.add(FloatPair(1f, 1f)) shouldBe 2f
            }

            "fun blockingAdd(pair: IntPair): Int" {
                proxy.blockingAdd(IntPair(2, 2)) shouldBe 4
            }

            "fun exception()" {
                shouldThrow<RSocketError.ApplicationError> { proxy.exception() }
            }

            "fun blockingException()" {
                shouldThrow<InvocationException> { proxy.blockingException() }
            }

            "fun blockingList(): List<Int>" {
                proxy.blockingList() shouldBe listOf(1, 2, 3)
            }
            "fun list(): List<Int>" {
                proxy.list() shouldBe listOf(1, 2, 3)
            }
        }

        "Request-Stream" - {
            "fun stream(): Flow<Int>" {
                proxy.stream().toList() shouldBe listOf(listOf(1,2), listOf(2, 3))
            }

            "fun blockingStream(): Flow<Int>" {
                proxy.blockingStream().toList() shouldBe listOf(listOf(1,2), listOf(2, 3))
            }
        }

    }


})
