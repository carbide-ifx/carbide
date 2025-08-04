package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass


interface IProtocol {
    fun <T : IService> expose(endpoint: Endpoint<T>): IProtocol
    fun <T : IService> createClientBinding(cls: KClass<T>): IBinding
    fun open(): IProtocol
    fun close(): IProtocol
    fun <T : IService> getAddress(contract: KClass<T>): String

    companion object {
        inline fun <reified T : IService> IProtocol.createClient(): IBinding = createClientBinding(T::class)
    }
}
