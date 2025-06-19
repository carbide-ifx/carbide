package ifx.protocol.contract

import ifx.service.IService
import java.lang.reflect.InvocationHandler
import kotlin.reflect.KClass

interface IProtocol {
    fun <T : IService> bind(contract: KClass<T>, instance: T): IProtocol
    fun start(): IProtocol
    fun stop(): IProtocol
    fun <T : IService> createClient(cls: KClass<T>): InvocationHandler
}

inline fun <reified T : IService> IProtocol.createClient(): InvocationHandler = createClient<T>(T::class)
