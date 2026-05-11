package ifx.proxy.contract

import ifx.protocol.contract.IInterceptor
import ifx.service.IService
import kotlin.reflect.KClass

interface IProxyFactory {
    fun <T : IService> create(contract: KClass<T>): T
    fun <T : IService> create(service: Class<T>): T = create(service.kotlin)
    fun addInterceptors(vararg i: IInterceptor): IProxyFactory
    fun addInterceptors(i: List<IInterceptor>): IProxyFactory

}

inline fun <reified T : IService> IProxyFactory.create(): T = create(T::class)
