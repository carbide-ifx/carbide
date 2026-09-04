package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ProtocolListenerDescription
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

/**
 * One protocol on one port, plus any extensions installed alongside it.
 *
 * Extensions belong to the listener they extend, so a host never has to reconcile an extension with
 * a listener it does not own.
 */
data class ProtocolListener(
    val protocol: IServerProtocol,
    val port: Int = 0,
    val host: String = "0.0.0.0",
    val id: String = protocol.id,
    val endpointSource: EndpointSource = EndpointSource.Registered,
    val extensions: List<HostExtension> = emptyList(),
)

/** Additional routes or capabilities installed into the listener that owns this extension. */
interface HostExtension {
    /** Protocol id this extension requires, or `null` when it works on any listener. */
    val requiredProtocolId: String? get() = null

    fun install(application: Application, context: HostExtensionContext)
}

/** Host state an extension can read while serving requests. */
class HostExtensionContext(
    val health: suspend () -> HostHealth = {
        HostHealth(HostState.NEW, ready = false, live = true, services = emptyList())
    },
)

interface IHost {
    val name: String
    val state: HostState

    fun <T : IService> registerService(descriptor: ServiceDescriptor<T>, instance: T): IHost
    suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        factory: suspend () -> T,
    ): IHost

    suspend fun start(): IHost
    suspend fun stop(): IHost
    suspend fun health(): HostHealth

    /**
     * Registers cleanup to run when the host stops, in reverse registration order. Use it to tie
     * resources a service depends on — a proxy factory, a connection pool — to the host lifetime.
     * Every action runs even if an earlier one fails.
     */
    fun onStop(action: () -> Unit): IHost

    companion object {
        inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            missingIfxCompilerPlugin()

        suspend inline fun <reified T : IService> IHost.registerService(noinline factory: suspend () -> T): IHost =
            missingIfxCompilerPlugin()
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost

    /** Mandatory client-safe interceptors followed by caller-supplied interceptors. */
    val interceptors: List<IInterceptor>
    val boundListeners: List<ProtocolListenerDescription>

    /** A live, read-only description of the services and resolved listeners exposed by this host. */
    fun serviceCatalog(): ServiceCatalog

    fun port(listenerId: String): Int = boundListeners.single { it.listenerId == listenerId }.port
}

@PublishedApi
internal fun missingIfxCompilerPlugin(): Nothing = error(
    "Typed IFX service registration requires the ifx.rpc.compiler plugin; " +
        "without it, pass the generated service descriptor explicitly",
)
