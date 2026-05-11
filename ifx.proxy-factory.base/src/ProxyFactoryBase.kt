package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.contract.ClientInterceptorPipeline
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.service.IService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Proxy
import kotlin.jvm.java
import kotlin.reflect.KClass

class ProxyFactoryBase(val protocol: IProtocol) : IProxyFactory {
    val interceptors: MutableList<IInterceptor> = mutableListOf()
    private val log = KotlinLogging.logger { }

    override fun addInterceptors(vararg i: IInterceptor): ProxyFactoryBase = apply { interceptors.addAll(i) }
    override fun addInterceptors(i: List<IInterceptor>): ProxyFactoryBase = apply { interceptors.addAll(i) }

    @Suppress("UNCHECKED_CAST")
    fun <T : IService> create(endpoint: Endpoint<T>): T {
        val interceptorPipeline = ClientInterceptorPipeline(
            interceptors = interceptors,
            nextHandler = protocol.createClientBinding(endpoint.contract),
        )
        return Proxy.newProxyInstance(
            endpoint.contract.java.classLoader,
            arrayOf<Class<T>>(endpoint.contract.java),
            EndpointInvocationHandler(interceptorPipeline)
        ) as T
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T : IService> create(contract: KClass<T>): T {
        val interceptorPipeline = ClientInterceptorPipeline(
            interceptors = interceptors,
            nextHandler = protocol.createClientBinding(contract),
        )
        return Proxy.newProxyInstance(
            contract.java.classLoader,
            arrayOf<Class<T>>(contract.java),
            EndpointInvocationHandler(interceptorPipeline)
        ) as T
    }
}


class DirectProxyFactory {
    val serviceMap: MutableMap<Class<*>, IService> = mutableMapOf()

    fun <T : IService> registerService(contract: KClass<T>, instance: T): DirectProxyFactory {
        serviceMap[contract.java] = instance
        return this
    }
     fun <T : IService> create(contract: KClass<T>): T = Proxy.newProxyInstance(
        contract.java.classLoader,
        arrayOf<Class<*>>(contract.java),
        InstanceHandler(serviceMap[contract.java] ?: error("No instance registered for $contract"))
    ) as T

}
