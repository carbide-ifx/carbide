package ifx.proxy.factory

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ClientInterceptorPipeline
import ifx.protocol.contract.serviceDescriptorOf
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import kotlin.reflect.KClass

class ProxyFactoryBase(
    val protocol: IProtocol,
) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    override fun <T : IService> create(contract: KClass<T>): T {
        val descriptor = serviceDescriptorOf(contract)
        val interceptorPipeline = ClientInterceptorPipeline(
            interceptors,
            protocol.createClientBinding(descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }
}
