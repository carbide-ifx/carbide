package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.service.IService
import io.ktor.server.application.Application
import kotlin.reflect.KClass

interface IServerProtocol {
    val id: String
    fun install(application: Application, endpoints: List<Endpoint>)
}

data class ProtocolListener(
    val protocol: IServerProtocol,
    val port: Int = 0,
    val host: String = "0.0.0.0",
)

data class BoundProtocolListener(
    val protocolId: String,
    val host: String,
    val port: Int,
)

/** Additional routes or capabilities installed into one host listener. */
interface HostExtension {
    val listener: ProtocolListener

    fun install(application: Application, context: HostExtensionContext)
}

class HostExtensionContext(
    val hostName: String,
    val endpoints: List<Endpoint>,
    val boundListeners: () -> List<BoundProtocolListener>,
)

interface IHost {

    val serviceDescriptors: ServiceDescriptorRegistry

    suspend fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    suspend fun <T : IService> registerService(contract: KClass<T>, factory: suspend () -> T): IHost

    fun open(): IHost
    fun close(): IHost

    companion object {
        suspend inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            registerService(T::class, instance)

        suspend inline fun <reified T : IService> IHost.registerService(noinline factory: suspend () -> T): IHost =
            registerService(T::class, factory)
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost
    val interceptors: List<IInterceptor>
    val boundListeners: List<BoundProtocolListener>

    fun port(protocolId: String): Int = boundListeners.single { it.protocolId == protocolId }.port
}
