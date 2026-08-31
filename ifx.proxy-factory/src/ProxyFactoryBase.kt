@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package ifx.proxy.factory

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.InterceptorPipeline
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService
import kotlin.concurrent.atomics.AtomicReference

class ProxyFactoryBase private constructor(
    val protocol: IClientProtocol,
    private val endpoint: ServiceEndpoint?,
    val interceptors: MutableList<IInterceptor>,
    private val bindings: AtomicReference<Map<BindingKey, IBinding>>,
) : IProxyFactory {
    constructor(protocol: IClientProtocol) : this(
        protocol = protocol,
        endpoint = null,
        interceptors = mutableListOf(),
        bindings = AtomicReference(emptyMap()),
    )

    /**
     * One binding, and therefore one connection, per destination and service address. Repeated
     * [create] calls for the same service reuse it, so proxies may be created per call site or per
     * request without accumulating transport resources.
     */
    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    override fun at(endpoint: ServiceEndpoint): ProxyFactoryBase = ProxyFactoryBase(
        protocol = protocol,
        endpoint = endpoint,
        interceptors = interceptors,
        bindings = bindings,
    )

    override fun <T : IService> create(descriptor: ServiceDescriptor<T>): T {
        val interceptorPipeline = InterceptorPipeline(
            descriptor.address,
            CallDirection.CLIENT,
            interceptors,
            bindingFor(endpoint, descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }

    override fun close() {
        bindings.store(emptyMap())
        protocol.close()
    }

    private fun bindingFor(endpoint: ServiceEndpoint?, address: String): IBinding {
        val key = BindingKey(endpoint, address)
        while (true) {
            val current = bindings.load()
            current[key]?.let { return it }

            // A binding that loses the race is discarded before it connects, so it holds nothing.
            val binding = protocol.createClientBinding(address, endpoint)
            if (bindings.compareAndSet(current, current + (key to binding))) return binding
        }
    }

    private data class BindingKey(
        val endpoint: ServiceEndpoint?,
        val address: String,
    )
}
