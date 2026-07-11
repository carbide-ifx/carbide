package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ServerInterceptorPipeline
import ifx.protocol.contract.ServiceRegistry
import ifx.service.IService
import kotlin.reflect.KClass

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
