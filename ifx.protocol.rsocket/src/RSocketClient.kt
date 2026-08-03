package ifx.protocol.rsocket

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@OptIn(RSocketLoggingApi::class)
class RSocketClient(url: String) : IBinding {
    val httpClient = HttpClient {
        this.install(WebSockets) // rsocket requires websockets plugin installed
        this.install(RSocketSupport) {
            // configure rSocket connector (all values have defaults)
            connector {
                loggerFactory = KermitRSocketLoggerFactory
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
    }
    val rsocketClient by lazy { runBlocking { async { httpClient.rSocket(url) } } }


    override suspend fun fireAndForget(operation: String, message: Message) =
        rsocketClient.await().fireAndForget(message.toRequestPayload(operation))


    override suspend fun requestResponse(operation: String, message: Message): Message =
        rsocketClient.await().requestResponse(message.toRequestPayload(operation)).toMessage()

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
        rsocketClient.await().requestStream(message.toRequestPayload(operation)).map { it.toMessage() }

}
