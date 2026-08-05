package ifx.protocol.rsocket

import ifx.host.IServerProtocol
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ProtocolException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import kotlinx.coroutines.flow.map

const val RSOCKET_PROTOCOL_ID: String = "rsocket"

@OptIn(RSocketLoggingApi::class)
class RSocketServerProtocol : IServerProtocol {
    override val id: String = RSOCKET_PROTOCOL_ID

    override fun install(application: Application, endpoints: List<Endpoint>) {
        application.install(WebSockets)
        application.install(RSocketSupport) {
            server {
                loggerFactory = KermitRSocketLoggerFactory
            }
        }
        application.routing {
            endpoints.forEach { endpoint ->
                rSocket(
                    path = endpoint.address,
                    acceptor = endpoint.connectionAcceptor(),
                )
            }
        }
    }

    private fun Endpoint.connectionAcceptor(): ConnectionAcceptor = ConnectionAcceptor {
        RSocketRequestHandler {
            fireAndForget { payload ->
                binding.fireAndForget(payload.metadata.route(), payload.toMessage())
            }
            requestResponse { payload ->
                binding.requestResponse(payload.metadata.route(), payload.toMessage()).toResponsePayload()
            }
            requestStream { payload ->
                binding.requestStream(payload.metadata.route(), payload.toMessage()).map { it.toResponsePayload() }
            }
        }
    }
}

class RSocketClientProtocol(
    private val baseUrl: () -> String,
) : IClientProtocol {
    constructor(host: String, port: Int) : this({ "ws://$host:$port" })

    override fun createClientBinding(address: String): IBinding = try {
        RSocketClient("${baseUrl().trimEnd('/')}/$address")
    } catch (exception: Throwable) {
        throw ProtocolException(exception) {
            "Failed to create RSocket client for $address: ${exception.message}"
        }
    }
}
