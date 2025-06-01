package ifx.host

import ifx.protocol.contract.IProtocol
import ifx.service.IService
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass


interface IHost {
    fun addProtocol(protocol: IProtocol): IHost

    fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    fun <T : IService> registerService(contract: KClass<T>, factory: (CoroutineContext) -> T): IHost

    fun start(): IHost
    fun stop(): IHost
}


inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
    registerService(T::class, instance)

inline fun <reified T : IService> IHost.registerService(noinline factory: (CoroutineContext) -> T): IHost =
    registerService(T::class, factory)
