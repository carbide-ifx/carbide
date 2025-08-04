package ifx.proxy.factory

import ifx.context.Context
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.InterceptorPipeline
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

class ProxyFactory(protocol: IProtocol? = null, val context: Context = Context()) : IProxyFactory {
    val protocol: IProtocol = protocol ?: RSocketProtocol()
    val interceptors: MutableList<IInterceptor> = mutableListOf()
    private val log = KotlinLogging.logger { }

    fun addInterceptors(vararg i: IInterceptor): ProxyFactory = apply { interceptors.addAll(i) }

    fun <T: IService> create(endpoint: Endpoint<T>): T {
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

