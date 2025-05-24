package ifx.rsocket.impl

import ifx.protocol.rsocket.IProtocol
import ifx.rsocket.read
import ifx.rsocket.route
import ifx.rsocket.rsocket.FloatPair
import ifx.rsocket.rsocket.IMyService
import ifx.rsocket.rsocket.IntPair
import ifx.rsocket.rsocket.MyService
import ifx.rsocket.toPayload
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.KClass

class HandcodedServer(private val instance: IMyService = MyService()) : IProtocol {


    private val server = embeddedServer(CIO, 8080) {
        install(WebSockets) // rsocket requires websockets plugin installed
        install(RSocketSupport)
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
            rSocket("IMyService", null, createAcceptor(instance))
        }
    }

    override fun <T : IService> bind(contract: KClass<T>, instance: T): IProtocol = TODO("Not yet implemented")

    override fun start(): IProtocol {
        server.start()
        return this
    }

    override fun stop(): IProtocol {
        server.stop()
        return this
    }
}


@OptIn(ExperimentalSerializationApi::class)
private fun createAcceptor(instance: IMyService): ConnectionAcceptor {
    return ConnectionAcceptor {

        RSocketRequestHandler {
            fireAndForget {
                try {
                    when (val route = it.route()) {
                        "hello()" -> instance.hello()
                        "blockingHello()" -> instance.blockingHello()
                        else -> error("Wrong route: $route")
                    }
                } catch (e: Exception) {
                    println("Server Error when processing RequestResponse: ${e.message}")
                    throw e
                }
            }
            requestResponse {
                try {
                    when (val route = it.route()) {
                        "add(IntPair):Int" -> instance.add(it.read<IntPair>()).toPayload()
                        "add(FloatPair):Float" -> instance.add(it.read<FloatPair>()).toPayload()
                        "blockingAdd(IntPair):Int" -> instance.blockingAdd(it.read<IntPair>()).toPayload()
                        "exception()" -> instance.exception().toPayload()
                        "blockingException()" -> instance.blockingException().toPayload()
                        else -> error("Wrong route: $route")
                    }

                } catch (e: Exception) {
                    println("Server Error when processing RequestResponse: ${e.message}")
                    throw e
                }
            }
            requestStream {
                try {
                    when (val route = it.route()) {
                        "stream():Flow" -> instance.stream().map { it.toPayload() }
                        "blockingStream():Flow" -> instance.blockingStream().map { it.toPayload() }
                        else -> error("Wrong route: $route")
                    }
                } catch (e: Exception) {
                    println("Server Error when processing RequestStream: ${e.message}")
                    throw e
                }
            }
        }
    }
}
