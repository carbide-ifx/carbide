package ifx.protocol.rsocket

import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.IProtocolServer
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.toPath
import ifx.service.IService
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
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class RSocketEndpoint(private val port: Int = 0) : IProtocolServer {
    private val server = server()
    private val acceptors = mutableMapOf<String, ConnectionAcceptor>()


    override fun exposeEndpoint(path: String, binding: IMessageHandler): IProtocolServer = apply {
        acceptors[path] = createAcceptor(binding)
    }

    override fun start(): IProtocolServer = apply { server.start() }

    override fun stop(): IProtocolServer = apply { server.stop() }

    private fun server() = embeddedServer(CIO, 0) {
        install(WebSockets) // rsocket requires websockets plugin installed
        install(RSocketSupport)
        routing {
            get("/") {
                call.respondText("TODO: Service Descriptors, test client, MEX should be here.")
            }
            acceptors.forEach {
                rSocket(path = it.key, acceptor = it.value)
            }
        }
    }

    override fun <T : IService> createClient(cls: KClass<T>) = try {
        val port = runBlocking { server.engine.resolvedConnectors().single().port }
        RSocketClient<T>("ws://localhost:$port/${cls.toPath()}")
    } catch (e: Throwable) {
        throw ProtocolException(e) { "Failed to create client for $cls: ${e.message}" }
    }

    companion object {
        private fun createAcceptor(binding: IMessageHandler) = ConnectionAcceptor {
            // Create session, etc
            RSocketRequestHandler {
                fireAndForget { payload ->
                    binding.fireAndForget(payload.route(), payload.toRequest())
                }
                requestResponse { payload ->
                    val result = binding.requestResponse(payload.route(),payload.toRequest())
                    result.toResponsePayload()
                }
                requestStream { payload ->
                    val result = binding.requestStream(payload.route(),payload.toRequest())
                    result.map { it.toResponsePayload() }
                }
            }
        }
    }
}
