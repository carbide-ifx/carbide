package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ServerInterceptorPipeline
import ifx.protocol.contract.ServiceRegistry
import ifx.service.IService
import kotlin.reflect.KClass

/**
 * Todo:
 *  - Leverage Dependency Injection (DI) Container for service resolution / late binding.
 *  - Incorporate protocol into endpoint address(instead of just path), to resolve protocol for each endpoint.
 *  - Some things are hardcoded here. Turn this into HostBase, and Leave `Host` to be implenented in each
 *      project-specific ifx. (E.g. Sonat-Conventions). To facilitate earlier release, this is left for later.
 */

class HostBase(
    override val protocol: IProtocol,
    override val registry: ServiceRegistry,
    val name: String = "Service Host",
    override val interceptors: MutableList<IInterceptor> = mutableListOf(),
) : IHost {
    val endpoints: MutableList<Endpoint> = mutableListOf()

    // TODO: Right now interceptors must be registered before services, breaking builder pattern expectations. Should fix!
    override fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost = apply {
        val descriptor = registry.descriptor(contract)
        val serviceBinding = descriptor.bind(instance)
        val interceptorBinding: IBinding = ServerInterceptorPipeline(interceptors = interceptors, nextBinding = serviceBinding)
        val endpoint = Endpoint(descriptor.address, interceptorBinding)
        endpoints.add(endpoint)
    }

    override fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost = apply {
        registerService(contract,factory())
    }

    override fun addInterceptors(vararg i: IInterceptor): IHost = apply { interceptors.addAll(i) }
    override fun addInterceptors(interceptors: List<IInterceptor>): IHost = apply { this.interceptors.addAll(interceptors) }

    override fun open(): IHost = apply {
        endpoints.forEach { endpoint ->
            protocol.expose(endpoint)
        }
        protocol.open()
    }

    override fun close(): IHost = apply { protocol.close() }
}
