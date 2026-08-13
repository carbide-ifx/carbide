package ifx.protocol.rsocket

import ifx.host.IServerProtocol
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ProtocolException
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

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

/**
 * Creates RSocket bindings that share one ktor client, and therefore one connection pool and one
 * RSocket connector configuration. [close] releases it; the bindings hold no resources of their own.
 *
 * [keepAlive] defaults to the subsystem window, since server-to-server calls are what this protocol
 * usually carries; pass [EXTERNAL_KEEP_ALIVE] for a client outside the backend. Tightening it also
 * tightens the derived [connectTimeout], which can be set independently when reconnecting through a
 * slow peer restart should be more patient than detecting a dead one.
 */
class RSocketClientProtocol(
    private val baseUrl: () -> String,
    keepAlive: KeepAlive = SUBSYSTEM_KEEP_ALIVE,
    private val connectTimeout: Duration = keepAlive.connectTimeout(),
) : IClientProtocol {
    constructor(host: String, port: Int) : this({ "ws://$host:$port" })

    private val httpClient: HttpClient = rsocketHttpClient(keepAlive)

    override fun createClientBinding(address: String): IBinding = try {
        RSocketClient(httpClient, "${baseUrl().trimEnd('/')}/$address", connectTimeout)
    } catch (exception: Throwable) {
        throw ProtocolException(exception) {
            "Failed to create RSocket client for $address: ${exception.message}"
        }
    }

    override fun close(): Unit = httpClient.close()
}
