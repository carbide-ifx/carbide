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
    private val state: AtomicReference<FactoryState>,
) : IProxyFactory {
    constructor(protocol: IClientProtocol) : this(
        protocol = protocol,
        endpoint = null,
        state = AtomicReference(FactoryState()),
    )

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = addInterceptors(i.toList())

    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply {
        updateState { current ->
            check(!current.closed) { "Cannot configure a closed proxy factory" }
            check(!current.configurationFrozen) {
                "Interceptors cannot be added after a proxy has been created"
            }
            current.copy(interceptors = current.interceptors + i)
        }
    }

    override fun at(endpoint: ServiceEndpoint): ProxyFactoryBase {
        checkOpen(state.load())
        return ProxyFactoryBase(
            protocol = protocol,
            endpoint = endpoint,
            state = state,
        )
    }

    override fun <T : IService> create(descriptor: ServiceDescriptor<T>): T {
        val interceptors = freezeConfiguration()
        val interceptorPipeline = InterceptorPipeline(
            descriptor.address,
            CallDirection.CLIENT,
            interceptors,
            bindingFor(endpoint, descriptor.address),
        )
        return descriptor.createClient(interceptorPipeline)
    }

    override fun close() {
        while (true) {
            val current = state.load()
            if (current.closed) return
            if (state.compareAndSet(current, current.copy(closed = true, bindings = emptyMap()))) {
                protocol.close()
                return
            }
        }
    }

    private fun bindingFor(endpoint: ServiceEndpoint?, address: String): IBinding {
        val key = BindingKey(endpoint, address)
        while (true) {
            val current = state.load()
            checkOpen(current)
            current.bindings[key]?.let { return it }

            // A binding that loses the race is discarded before it connects, so it holds nothing.
            val binding = protocol.createClientBinding(address, endpoint)
            if (state.compareAndSet(current, current.copy(bindings = current.bindings + (key to binding)))) {
                return binding
            }
        }
    }

    private fun freezeConfiguration(): List<IInterceptor> {
        while (true) {
            val current = state.load()
            checkOpen(current)
            if (current.configurationFrozen) return current.interceptors
            val frozen = current.copy(configurationFrozen = true)
            if (state.compareAndSet(current, frozen)) return frozen.interceptors
        }
    }

    private fun updateState(transform: (FactoryState) -> FactoryState) {
        while (true) {
            val current = state.load()
            val updated = transform(current)
            if (state.compareAndSet(current, updated)) return
        }
    }

    private fun checkOpen(current: FactoryState) {
        check(!current.closed) { "Proxy factory is closed" }
    }

    private data class FactoryState(
        val interceptors: List<IInterceptor> = emptyList(),
        val configurationFrozen: Boolean = false,
        val closed: Boolean = false,
        val bindings: Map<BindingKey, IBinding> = emptyMap(),
    )

    private data class BindingKey(
        val endpoint: ServiceEndpoint?,
        val address: String,
    )
}
