@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package ifx.proxy.factory

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ClientInterceptorPipeline
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.ServiceDescriptor
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import kotlin.concurrent.atomics.AtomicReference

class ProxyFactoryBase(
    val protocol: IClientProtocol,
) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()

    /**
     * One binding, and therefore one connection, per service address. Repeated [create] calls for
     * the same service reuse it, so proxies may be created per call site or per request without
     * accumulating transport resources.
     */
    private val bindings = AtomicReference<Map<String, IBinding>>(emptyMap())

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    override fun <T : IService> create(descriptor: ServiceDescriptor<T>): T {
        val interceptorPipeline = ClientInterceptorPipeline(
            descriptor.address,
            interceptors,
            bindingFor(descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }

    override fun close() {
        bindings.store(emptyMap())
        protocol.close()
    }

    private fun bindingFor(address: String): IBinding {
        while (true) {
            val current = bindings.load()
            current[address]?.let { return it }

            // A binding that loses the race is discarded before it connects, so it holds nothing.
            val binding = protocol.createClientBinding(address)
            if (bindings.compareAndSet(current, current + (address to binding))) return binding
        }
    }
}
