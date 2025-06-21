package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass


interface IProtocolServer {
    fun exposeEndpoint(path: String, binding: IMessageHandler): IProtocolServer
    fun start(): IProtocolServer
    fun stop(): IProtocolServer
    fun <T : IService> createClient(cls: KClass<T>): IMessageHandler

    companion object {
        inline fun <reified T : IService> IProtocolServer.expose(handler: IMessageHandler): IProtocolServer = exposeEndpoint(T::class.toPath(), handler)
        inline fun <reified T : IService> IProtocolServer.createClient(): IMessageHandler = createClient(T::class)
    }
}

fun <T : IService> KClass<T>.toPath(): String = simpleName
    ?: throw IllegalArgumentException("Service class $this must have a simple name")
