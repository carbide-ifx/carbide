package ifx.protocol

import ifx.service.IService
import java.lang.reflect.InvocationHandler
import kotlin.reflect.KClass

interface IProtocol {
    fun <T : IService> bind(contract: KClass<T>, instance: T): IProtocol
    fun start(): IProtocol
    fun stop(): IProtocol
    fun <T : IService> createHandler(cls: KClass<T>): InvocationHandler
}

inline fun <reified T : IService> IProtocol.createHandler(): InvocationHandler = createHandler<T>(T::class)
