package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.InterceptorPipeline
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

class ProxyFactoryBase(val protocol: IProtocol) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()
    private val log = KotlinLogging.logger { }

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }

    @Suppress("UNCHECKED_CAST")
    fun <T : IService> create(endpoint: Endpoint<T>): T {
        val interceptorPipeline: InterceptorPipeline = InterceptorPipeline(
            requestInterceptors = interceptors,
            responseInterceptors = interceptors.reversed(),
            nextHandler = protocol.createClientBinding<T>(endpoint.contract)
        )
        return Proxy.newProxyInstance(
            endpoint.contract.java.classLoader,
            arrayOf<Class<T>>(endpoint.contract.java),
            EndpointInvocationHandler(interceptorPipeline)
        ) as T
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T : IService> create(contract: KClass<T>): T {
        val interceptorPipeline = InterceptorPipeline(
            requestInterceptors = interceptors,
            responseInterceptors = interceptors.reversed(),
            nextHandler = protocol.createClientBinding(contract)
        )
        return Proxy.newProxyInstance(
            contract.java.classLoader,
            arrayOf<Class<T>>(contract.java),
            EndpointInvocationHandler(interceptorPipeline)
        ) as T
    }
}
