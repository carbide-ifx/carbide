package ifx.protocol.rsocket

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ProtocolException
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import kotlinx.coroutines.flow.map

class RSocketProtocol(val port: Int = 0) : IProtocol {

    private val acceptors = mutableMapOf<String, ConnectionAcceptor>()
    private val server = embeddedServer(CIO, port) {
        install(WebSockets)
        install(RSocketSupport)
        routing {
            get("/") {
                call.respondText("TODO: Service Descriptors, test client, MEX should be here.")
            }
            acceptors.forEach { (route, acceotor) ->
                rSocket(path = route, acceptor = acceotor)
            }
        }
    }

    override fun open(): IProtocol = apply {
        server.start()
    }

    override fun close(): IProtocol = apply { server.stop() }


    override fun createClientBinding(address: String): IBinding = try {
        RSocketClient("ws://localhost:$port/$address")
    } catch (e: Throwable) {
        throw ProtocolException(e) { "Failed to create client for $address: ${e.message}" }
    }

    override fun expose(endpoint: Endpoint): IProtocol = apply {
        acceptors[endpoint.address] = ConnectionAcceptor {
            RSocketRequestHandler {
                fireAndForget { payload ->
                    endpoint.binding.fireAndForget(payload.metadata.route(), payload.toMessage())
                }
                requestResponse { payload ->
                    val result = endpoint.binding.requestResponse(payload.metadata.route(), payload.toMessage())
                    result.toResponsePayload()
                }
                requestStream { payload ->
                    val result = endpoint.binding.requestStream(payload.metadata.route(), payload.toMessage())
                    result.map { it.toResponsePayload() }
                }
            }
        }

    }
}
