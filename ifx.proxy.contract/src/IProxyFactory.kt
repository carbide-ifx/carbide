package ifx.proxy.contract

import ifx.service.IService
import kotlin.reflect.KClass

interface IProxyFactory {
    fun <T : IService> create(contract: KClass<T>): T
    fun <T : IService> create(service: Class<T>): T = create(service.kotlin)
}

inline fun <reified T : IService> IProxyFactory.create(): T = create(T::class)
