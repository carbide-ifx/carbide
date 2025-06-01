package ifx.protocol.rsocket

import ifx.protocol.contract.IProtocol
import ifx.proxy.rsocket.RsocketInvocationHandler
import ifx.proxy.rsocket.buildRsocket
import ifx.proxy.rsocket.methodsFor
import ifx.rsocket.format
import ifx.rsocket.route
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
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.valueParameters


class RSocketProtocol : IProtocol {

    private val server = server()
    private val acceptors = mutableMapOf<String, ConnectionAcceptor>()

    override fun <T : IService> createHandler(cls: KClass<T>)= RsocketInvocationHandler(buildRsocket(cls.simpleName!!))

    inline fun <reified T : IService> bind(instance: T) = bind(T::class, instance)
    override fun <T : IService> bind(contract: KClass<T>, instance: T): IProtocol {
        acceptors[contract.simpleName!!] = createAcceptor(contract, instance)
        return this
    }


    override fun start(): IProtocol {
        server.start()
        return this
    }

    override fun stop(): IProtocol {
        server.stop()
        return this
    }

    private fun server() = embeddedServer(CIO, 8080) {
        install(WebSockets) // rsocket requires websockets plugin installed
        install(RSocketSupport)
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
            acceptors.forEach {
                rSocket(it.key, null, it.value)
            }
        }
    }
}

private fun Payload.asArgFor(function: KFunction<*>): Any? {
    val paramType = function.valueParameters.singleOrNull()?.type ?: return null
    return format.decodeFromString(serializer(paramType), data.readString())
}

private suspend operator fun <R> KFunction<R>.invoke(instance: Any, payload: Payload): R {
    val args = listOfNotNull(instance, payload.asArgFor(this)).toTypedArray()
    return callSuspend(*args)
}

@OptIn(ExperimentalSerializationApi::class)
private fun <T : IService> createAcceptor(contract: KClass<T>, instance: T): ConnectionAcceptor {
    return ConnectionAcceptor {
        val allMethods = methodsFor(contract)
        // Create session, etc
        RSocketRequestHandler {
            fireAndForget { payload ->
                val route = payload.route()
                val method = allMethods[route]
                    ?: error("Method $route not found. Registered methods: ${allMethods.keys}")
                method(instance, payload)
            }
            requestResponse { payload ->
                val route = payload.route()
                val method = allMethods[route]
                    ?: error("Method $route not found. Registered methods: ${allMethods.keys}")
                method(instance, payload).toPayload(method.returnType)
            }

            requestStream { payload ->
                val route = payload.route()
                val method = allMethods[route]
                    ?: error("Method $route not found. Registered methods: ${allMethods.keys}")
                val result = method(instance, payload) as Flow<*>
                result.map { it.toPayload(method.flowType()) }
            }
        }
    }
}

private fun KFunction<*>.flowType(): KType = returnType.arguments.single().type!!

