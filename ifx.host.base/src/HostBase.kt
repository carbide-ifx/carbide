package ifx.host

import ifx.logging.Log
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.InterceptorPipeline
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.factory.ProxyFactoryBase
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
    val name: String = "Service Host",
    override val interceptors: MutableList<IInterceptor> = mutableListOf(),
) : IHost {
    val log = Log {}
    val endpoints: MutableList<Endpoint<*>> = mutableListOf()

    // TODO: Right now interceptors must be registered before services, breaking builder pattern expectations. Should fix!
    override fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost = apply {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        val address = protocol.getAddress(contract)
        val serviceBinding: IBinding = ServiceBinding(contract, instance)
        val interceptorBinding = InterceptorPipeline(
            requestInterceptors = interceptors,
            responseInterceptors = interceptors.reversed(),
            nextHandler = serviceBinding
        )
        val endpoint = Endpoint(address, interceptorBinding, contract)
        endpoints.add(endpoint)
    }

    override fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost = apply {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        log.warn { "Service [${contract.qualifiedName}] registered with factory, but stored as singleton (Pending DI TODO)" }
        registerService(contract,factory())
    }

    override fun addInterceptors(vararg i: IInterceptor): IHost = apply { interceptors.addAll(i) }

    override fun open(): IHost = apply {
        endpoints.forEach { endpoint ->
            protocol.expose(endpoint)
        }
        protocol.open()
        log.info { "HOST OPENED" }
    }

    override fun close(): IHost = apply { protocol.close() }
}
