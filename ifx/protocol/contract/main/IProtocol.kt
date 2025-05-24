package ifx.protocol.rsocket

import ifx.service.IService
import kotlin.reflect.KClass

interface IProtocol {
    fun <T: IService> bind(contract: KClass<T>, instance: T): IProtocol
    fun start(): IProtocol
    fun stop(): IProtocol
}
