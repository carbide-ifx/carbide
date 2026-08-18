package ifx.protocol.rsocket

import ifx.context.Context
import ifx.host.IServerProtocol
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.withContext
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketError
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

const val RSOCKET_PROTOCOL_ID: String = "rsocket"

/** Authenticates one RSocket SETUP payload and returns context trusted for the connection. */
fun interface RSocketSetupAuthenticator {
    suspend fun authenticate(setupPayload: Payload): Context?
}

@OptIn(RSocketLoggingApi::class)
class RSocketServerProtocol(
    private val authenticator: RSocketSetupAuthenticator? = null,
) : IServerProtocol {
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
        val trustedContext = try {
            authenticator?.authenticate(config.setupPayload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (authenticator != null && trustedContext == null) {
            throw RSocketError.Setup.Rejected("Authentication required")
        }
        RSocketRequestHandler {
            fireAndForget { payload ->
                binding.fireAndForget(payload.metadata.route(), payload.toMessage(trustedContext))
            }
            requestResponse { payload ->
                binding.requestResponse(payload.metadata.route(), payload.toMessage(trustedContext)).toResponsePayload()
            }
            requestStream { payload ->
                binding.requestStream(payload.metadata.route(), payload.toMessage(trustedContext)).map { it.toResponsePayload() }
            }
        }
    }

    private fun Payload.toMessage(trustedContext: Context?): Message =
        toMessage().let { message -> trustedContext?.let(message::withContext) ?: message }
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
    setupData: () -> String = { """{ "data": "setup" }""" },
) : IClientProtocol {
    constructor(host: String, port: Int) : this({ "ws://$host:$port" })

    private val httpClient: HttpClient = rsocketHttpClient(keepAlive, setupData)

    override fun createClientBinding(address: String): IBinding = try {
        RSocketClient(httpClient, "${baseUrl().trimEnd('/')}/$address", connectTimeout)
    } catch (exception: Throwable) {
        throw ProtocolException(exception) {
            "Failed to create RSocket client for $address: ${exception.message}"
        }
    }

    override fun close(): Unit = httpClient.close()
}
