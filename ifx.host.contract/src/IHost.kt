package ifx.host

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.ServiceRegistry
import ifx.service.IService
import kotlin.reflect.KClass


interface IHost {

    fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost

    fun open(): IHost
    fun close(): IHost

    companion object {
        inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            registerService(T::class, instance)

        inline fun <reified T : IService> IHost.registerService(noinline factory: () -> T): IHost =
            registerService(T::class, factory)
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost
    val interceptors: List<IInterceptor>
    val protocol: IProtocol
    val registry: ServiceRegistry
}
