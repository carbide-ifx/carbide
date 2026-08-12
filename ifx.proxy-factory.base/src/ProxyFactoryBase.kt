package ifx.proxy.factory

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ClientInterceptorPipeline
import ifx.protocol.contract.ServiceDescriptor
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService

class ProxyFactoryBase(
    val protocol: IClientProtocol,
) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    override fun <T : IService> create(descriptor: ServiceDescriptor<T>): T {
        val interceptorPipeline = ClientInterceptorPipeline(
            descriptor.address,
            interceptors,
            protocol.createClientBinding(descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }
}
