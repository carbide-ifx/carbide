package ifx.protocol.rsocket

import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.Message
import ifx.service.IService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class RSocketClient<T : IService>(url: String) : IMessageHandler {
    val client = runBlocking {
        HttpClient {
            this.install(WebSockets) // rsocket requires websockets plugin installed
            this.install(RSocketSupport) {
                // configure rSocket connector (all values have defaults)
                connector {
                    connectionConfig {
                        // payload for setup frame
                        setupPayload {
                            buildPayload {
                                data("""{ "data": "setup" }""")
                            }
                        }
                        // mime types
                        payloadMimeType = PayloadMimeType(
                            data = WellKnownMimeType.ApplicationJson,
                            metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                        )
                    }
                }
            }
        }.rSocket(url)
    }

    override suspend fun fireAndForget(operation: String, message: Message) =
        client.fireAndForget(message.toRequestPayload(operation))


    override suspend fun requestResponse(operation: String, message: Message): Message =
        client.requestResponse(message.toRequestPayload(operation)).toMessage()

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
        client.requestStream(message.toRequestPayload(operation)).map { it.toMessage() }

}
