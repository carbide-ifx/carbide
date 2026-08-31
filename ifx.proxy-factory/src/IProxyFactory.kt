package ifx.proxy.factory

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService

interface IProxyFactory {
    fun <T : IService> create(descriptor: ServiceDescriptor<T>): T

    /**
     * Returns a lightweight view whose proxies connect to [endpoint]. The view shares this factory's
     * transport, interceptors, binding cache, and lifecycle.
     */
    fun at(endpoint: ServiceEndpoint): IProxyFactory

    fun addInterceptors(vararg i: IInterceptor): IProxyFactory
    fun addInterceptors(i: List<IInterceptor>): IProxyFactory

    /**
     * Releases the connections shared by the proxies this factory created. Proxies outlive the
     * factory as objects but cannot make calls once it is closed.
     */
    fun close()
}

inline fun <reified T : IService> IProxyFactory.create(): T = missingIfxCompilerPlugin()

@PublishedApi
internal fun missingIfxCompilerPlugin(): Nothing = error(
    "Typed IFX proxy creation requires the ifx.rpc.compiler plugin; " +
        "without it, pass the generated service descriptor explicitly",
)
