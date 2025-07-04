package ifx.host

import ifx.protocol.contract.ExtensionPipeline
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.IProtocolServer
import ifx.protocol.contract.toPath
import ifx.service.IService
import kotlin.reflect.KClass

class Host : IHost {
    private val protocols: MutableList<IProtocolServer> = mutableListOf()
    private val services: MutableMap<KClass<out IService>, IService> = mutableMapOf()
    private val interceptors: MutableList<IInterceptor> = mutableListOf()

    override fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost = apply {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        services[contract] = instance
    }

    override fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost = apply {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        services[contract] = factory()
    }

    override fun addProtocol(protocol: IProtocolServer): IHost = apply {
        protocols.add(protocol)
    }

    override fun addInterceptors(vararg i: IInterceptor): IHost = apply{
        interceptors.addAll(i)
    }

    override fun start(): IHost = apply {
        protocols.forEach { protocol ->
            services.forEach { (contract, instance) ->
                val binding: IMessageHandler = ServiceBinding(contract, instance)
                val requestPipeline = ExtensionPipeline(
                    requestInterceptors = interceptors,
                    responseInterceptors = interceptors.reversed(),
                    nextHandler = binding
                )
                protocol.exposeEndpoint(contract.toPath(), requestPipeline)
            }
            protocol.start()
        }
    }

    override fun stop(): IHost = apply {
        protocols.forEach { it.stop() }
    }
}

