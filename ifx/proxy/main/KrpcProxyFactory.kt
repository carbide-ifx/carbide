package ifx.proxy

import ifx.context.Context
import ifx.service.SonatConvention
import io.ktor.client.HttpClient
import io.ktor.http.encodedPath
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import kotlinx.serialization.json.Json

object KrpcProxyFactory {
    val ktorClient = HttpClient {
        installKrpc {}
    }

    suspend inline fun <@Rpc reified T : RemoteService> create(): T {
        val currentCtx = currentCoroutineContext()[Context].also { println("Proxy: Current context: $it") }
        val context = currentCtx ?: Context().also { println("Proxy: No context found, using default: $it") }

        return ktorClient.rpc {
            headers[Context.HEADER_KEY] = Json.encodeToString(context)
            url {
                host = "localhost"
                port = 8080
                encodedPath = SonatConvention.getPath<T>()
            }
            rpcConfig {

                serialization {
                    json{}
                }
            }
        }.withService<T>()
    }
}





