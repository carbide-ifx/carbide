package ifx.protocol.rsocket

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ProtocolException
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

class RSocketProtocol(private val port: Int = 0) : IProtocol {
    private val acceptors = mutableMapOf<String, ConnectionAcceptor>()
    private val server = embeddedServer(CIO, port) {
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

    override fun open(): IProtocol = apply { server.start() }

    override fun close(): IProtocol = apply { server.stop() }


    override fun <T : IService> createClientBinding(cls: KClass<T>): IBinding = try {
        val port = runBlocking { server.engine.resolvedConnectors().single().port }
        RSocketClient<T>("ws://localhost:$port/${this.getAddress(cls)}")
    } catch (e: Throwable) {
        throw ProtocolException(e) { "Failed to create client for $cls: ${e.message}" }
    }

    override fun <T : IService> expose(endpoint: Endpoint<T>): IProtocol = apply {
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

    override fun <T : IService> getAddress(contract: KClass<T>): String = Companion.getAddress(contract)

    companion object {
        fun <T : IService> getAddress(contract: KClass<T>): String = contract.simpleName
            ?: throw IllegalArgumentException("Service class $contract must have a simple name")

        inline fun <reified T : IService> createEndpoint(binding: IBinding): Endpoint<T> =
            Endpoint(getAddress(T::class), binding, T::class)
    }
}
