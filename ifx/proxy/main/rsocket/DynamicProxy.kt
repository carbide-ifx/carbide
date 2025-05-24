package ifx.proxy.rsocket

import ifx.service.IService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy

object ProxyFactory {

    inline fun <reified T : IService> create(instance: T? = null): T {
        val contract = T::class.java
        val interfaces = if(contract.isInterface) arrayOf(contract) else contract.interfaces
        return Proxy.newProxyInstance(
            contract.classLoader,
            interfaces,
            instance
                ?.let { InstanceHandler(it) }
                ?: RsocketInvocationHandler(buildRsocket(T::class.simpleName!!)),
        ) as T
    }

    fun buildRsocket(route: String): RSocket = runBlocking {
        HttpClient {
            install(WebSockets) // rsocket requires websockets plugin installed
            install(RSocketSupport) {
                // configure rSocket connector (all values have defaults)
                connector {
                    connectionConfig {
                        // payload for setup frame
                        setupPayload {
                            buildPayload {
                                data("""{ "data": "setup" }""")
                            }
                        }
                        // mime types
                        payloadMimeType = PayloadMimeType(
                            data = WellKnownMimeType.ApplicationJson,
                            metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                        )
                    }
                }
            }
        }.rSocket("ws://localhost:8080/${route}")
    }

}

