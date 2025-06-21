package ifx.rsocket.rsocket

import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.Message
import ifx.protocol.contract.IProtocolServer.Companion.createClient
import ifx.protocol.contract.toPath
import ifx.protocol.rsocket.RSocketEndpoint
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


val handler = TestHandler()
val server = RSocketEndpoint().exposeEndpoint(IMyService::class.toPath(), handler).start()
val client = server.createClient<IMyService>()

class RSocketProtocolTest {

    @Test
    fun `Fire and forget`(): Unit = runBlocking {
        handler.flag shouldBe false
        client.fireAndForget(operation = "setFlag", Message(header = "faf", body = "faf"))
        eventually(1.seconds) {
            handler.flag shouldBe true
        }
    }

    @Test
    fun `Request-Response`(): Unit = runBlocking {
        client.requestResponse(operation = "add-one",Message(header = "rr", body = "1"))
            .body shouldBe Message("response", "2").body
    }

    @Test
    fun `Request-Stream`(): Unit = runBlocking {
        client.requestStream(operation = "numbers", Message("echo", "5")).toList() shouldBe listOf(
            Message("", "0"),
            Message("", "1"),
            Message("", "2"),
            Message("", "3"),
            Message("", "4"),
            Message("", "5")
        )
    }
}


class TestHandler : IMessageHandler {
    var flag = false
    override suspend fun fireAndForget(operation: String, message: Message) = when (operation) {
        "setFlag" -> flag = true.also { println("Flag set!") }
        else -> error("Unhandled method: $operation")

    }

    override suspend fun requestResponse(operation: String, message: Message): Message = when (operation) {
        "add-one" -> Message("response", (message.body.toInt() + 1).toString())
        else -> error("Unhandled method: $operation")
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = when (operation) {
        "numbers" -> 0.rangeTo(message.body.toInt()).map { Message(message.header, it.toString()) }.asFlow()
        else -> error("Unhandled method: $operation")
    }
}
