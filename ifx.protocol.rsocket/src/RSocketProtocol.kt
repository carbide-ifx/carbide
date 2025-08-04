package ifx.protocol.rsocket

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ProtocolException
import ifx.service.IService
import io.github.oshai.kotlinlogging.KotlinLogging
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
import java.net.ServerSocket
import kotlin.reflect.KClass

class RSocketProtocol(val portToUse: Int = 0) : IProtocol {
    val port: Int = if(portToUse == 0) findFreePort() else portToUse
    private val log = KotlinLogging.logger { }
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
        log.info { "RSocket Protocol opened, listening on $port" }
    }

    override fun close(): IProtocol = apply { server.stop() }


    override fun <T : IService> createClientBinding(cls: KClass<T>): IBinding = try {
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
        fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

        fun <T : IService> getAddress(contract: KClass<T>): String = contract.simpleName
            ?: throw IllegalArgumentException("Service class $contract must have a simple name")

        inline fun <reified T : IService> createEndpoint(binding: IBinding): Endpoint<T> =
            Endpoint(getAddress(T::class), binding, T::class)
    }
}
