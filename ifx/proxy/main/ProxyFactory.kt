package ifx.proxy

import ifx.service.SonatConvention
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.rpc.RemoteService
import kotlinx.rpc.krpc.ktor.client.installRPC
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import io.ktor.client.plugins.HttpTimeout
object ProxyFactory {
    val client = HttpClient {
        installRPC {

        }
    }

    suspend inline fun <reified T : RemoteService> create(): T {

        val proxyCfg = currentCoroutineContext()[ProxyFactoryConfig] ?: ProxyFactoryConfig()
        return client.rpc {
            url {
                host = "localhost"
                port = 8080
                encodedPath = SonatConvention.getPath<T>()
            }
            rpcConfig {
                serialization {
                    json()
                }
            }
            header("Arve", "Arve")
        }

            .withService<T>()

    }
}

