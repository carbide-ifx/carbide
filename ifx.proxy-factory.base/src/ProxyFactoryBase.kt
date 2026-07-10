package ifx.proxy.factory

import ifx.protocol.contract.ClientInterceptorPipeline
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ServiceRegistry
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import kotlin.reflect.KClass

class ProxyFactoryBase(
    val protocol: IProtocol,
    val registry: ServiceRegistry,
) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    override fun <T : IService> create(contract: KClass<T>): T {
        val descriptor = registry.descriptor(contract)
        val interceptorPipeline = ClientInterceptorPipeline(
            interceptors = interceptors,
            nextHandler = protocol.createClientBinding(descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }
}
