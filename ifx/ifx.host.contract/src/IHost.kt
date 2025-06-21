package ifx.host

import ifx.protocol.contract.IProtocolServer
import ifx.service.IService
import kotlin.reflect.KClass


interface IHost {
    fun addProtocol(protocol: IProtocolServer): IHost

    fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost

    fun start(): IHost
    fun stop(): IHost

    companion object {
        inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            registerService(T::class, instance)

        inline fun <reified T : IService> IHost.registerService(noinline factory: () -> T): IHost =
            registerService(T::class, factory)
    }
}


