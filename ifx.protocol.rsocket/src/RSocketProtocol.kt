package ifx.protocol.rsocket

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescription
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class RSocketProtocol(
    private val requestedPort: Int = 0,
    private val hostName: String = "Service Host",
    private val testUiEnabled: Boolean = false,
) : IProtocol {

    private val acceptors = mutableMapOf<String, ConnectionAcceptor>()
    private val descriptions = mutableMapOf<String, ServiceDescription>()
    private val server = embeddedServer(CIO, requestedPort) {
        install(WebSockets)
        install(RSocketSupport)
        routing {
            if (testUiEnabled) {
                get("/") {
                    call.respondText(TestUiAssets.html, ContentType.Text.Html)
                }
                get("/ifx/services") {
                    call.respondText(
                        RpcFormat.encodeToString(ServiceCatalog(hostName, descriptions.values.toList())),
                        ContentType.Application.Json,
                    )
                }
                get("/ifx/test-ui.js") {
                    call.respondText(TestUiAssets.javascript, ContentType.Application.JavaScript)
                }
            }
            acceptors.forEach { (route, acceotor) ->
                rSocket(path = route, acceptor = acceotor)
            }
        }
    }
    var port: Int = requestedPort
        private set

    override fun open(): IProtocol = apply {
        server.start()
        port = runBlocking {
            server.engine.resolvedConnectors().single().port
        }
    }

    override fun close(): IProtocol = apply { server.stop() }


    override fun createClientBinding(address: String): IBinding = try {
        RSocketClient("ws://localhost:$port/$address")
    } catch (e: Throwable) {
        throw ProtocolException(e) { "Failed to create client for $address: ${e.message}" }
    }

    override fun expose(endpoint: Endpoint): IProtocol = apply {
        descriptions[endpoint.address] = endpoint.description
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
