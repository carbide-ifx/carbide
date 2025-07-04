package ifx.proxy.factory

import ifx.protocol.contract.ExtensionPipeline
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocolServer
import ifx.protocol.rsocket.RSocketEndpoint
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

class ProxyFactory(protocol: IProtocolServer? = null) : IProxyFactory {
    val protocol: IProtocolServer = protocol ?: RSocketEndpoint()
    val interceptors: MutableList<IInterceptor> = mutableListOf()


    fun addInterceptors(vararg i: IInterceptor): ProxyFactory = apply { interceptors.addAll(i) }

    override fun <T : IService> create(contract: KClass<T>): T {
        val extensionPipeline: ExtensionPipeline = ExtensionPipeline(
            requestInterceptors = interceptors,
            responseInterceptors = interceptors.reversed(),
            nextHandler = protocol.createClient(contract)
        )
        return Proxy.newProxyInstance(
            contract.java.classLoader,
            arrayOf<Class<T>>(contract.java),
            EndpointInvocationHandler(extensionPipeline)
        ) as T
    }
}

