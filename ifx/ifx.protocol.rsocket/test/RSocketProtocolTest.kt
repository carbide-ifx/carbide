package ifx.rsocket.rsocket

import ifx.logging.Log
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol.Companion.createClient
import ifx.protocol.contract.Message
import ifx.protocol.rsocket.RSocketProtocol
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


val handler = TestHandler()
val endpoint = RSocketProtocol.createEndpoint<IMyService>(handler)
val protocol = RSocketProtocol().expose(endpoint).open()
val client = protocol.createClient<IMyService>()

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
        client.requestResponse(operation = "add-one", Message(header = "rr", body = "1"))
            .body shouldBe Message("response", "2").body
    }

    @Test
    fun `Request-Stream`(): Unit = runBlocking {
        client.requestStream(operation = "numbers", Message("echo", "5")).toList() shouldBe listOf(
            Message("echo", "0"),
            Message("echo", "1"),
            Message("echo", "2"),
            Message("echo", "3"),
            Message("echo", "4"),
            Message("echo", "5")
        )
    }
}


class TestHandler : IBinding {
    val log = Log {}
    var flag = false

    override suspend fun fireAndForget(operation: String, message: Message) = when (operation) {
        "setFlag" -> flag = true.also { log.info { "Flag set!" } }
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
