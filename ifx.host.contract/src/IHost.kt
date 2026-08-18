package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService
import io.ktor.server.application.Application

interface IServerProtocol {
    val id: String
    fun install(application: Application, endpoints: List<Endpoint>)
}

/** Resolves the immutable endpoint set installed into one listener when its host opens. */
fun interface EndpointSource {
    fun endpoints(registeredEndpoints: List<Endpoint>): List<Endpoint>

    companion object {
        val Registered: EndpointSource = EndpointSource(List<Endpoint>::toList)
    }
}

data class ProtocolListener(
    val protocol: IServerProtocol,
    val port: Int = 0,
    val host: String = "0.0.0.0",
    val id: String = protocol.id,
    val endpointSource: EndpointSource = EndpointSource.Registered,
)

data class BoundProtocolListener(
    val protocolId: String,
    val host: String,
    val port: Int,
    val id: String = protocolId,
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
    val name: String

    suspend fun <T : IService> registerService(descriptor: ServiceDescriptor<T>, instance: T): IHost
    suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        factory: suspend () -> T,
    ): IHost

    fun open(): IHost
    fun close(): IHost

    /**
     * Registers cleanup to run when the host closes, in reverse registration order. Use it to tie
     * resources a service depends on — a proxy factory, a connection pool — to the host lifetime.
     * Every action runs even if an earlier one fails.
     */
    fun onClose(action: () -> Unit): IHost

    companion object {
        suspend inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            missingIfxCompilerPlugin()

        suspend inline fun <reified T : IService> IHost.registerService(noinline factory: suspend () -> T): IHost =
            missingIfxCompilerPlugin()
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost

    /** Mandatory client-safe interceptors followed by caller-supplied interceptors. */
    val interceptors: List<IInterceptor>
    val boundListeners: List<BoundProtocolListener>

    /** A live, read-only description of the services and resolved listeners exposed by this host. */
    fun serviceCatalog(): ServiceCatalog

    fun port(listenerId: String): Int = boundListeners.single { it.id == listenerId }.port
}

@PublishedApi
internal fun missingIfxCompilerPlugin(): Nothing = error(
    "Typed IFX service registration requires the ifx.rpc.compiler plugin; " +
        "without it, pass the generated service descriptor explicitly",
)
