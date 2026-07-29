package ifx.host

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.service.IService
import kotlin.reflect.KClass


interface IHost {

    suspend fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    suspend fun <T : IService> registerService(contract: KClass<T>, factory: suspend () -> T): IHost

    fun open(): IHost
    fun close(): IHost

    companion object {
        suspend inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            registerService(T::class, instance)

        suspend inline fun <reified T : IService> IHost.registerService(noinline factory: suspend () -> T): IHost =
            registerService(T::class, factory)
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost
    val interceptors: List<IInterceptor>
    val protocol: IProtocol
}
