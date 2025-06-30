package ifx.host

import ifx.protocol.contract.filters.LoggingFilter
import ifx.protocol.contract.ExtensionPipeline
import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.IProtocolServer
import ifx.protocol.contract.filters.Rot13Filter
import ifx.protocol.contract.toPath
import ifx.service.IService
import kotlin.reflect.KClass

class Host : IHost {
    private val protocols = mutableListOf<IProtocolServer>()
    private val services = mutableMapOf<KClass<out IService>, IService>()


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

    override fun start(): IHost = apply {
        protocols.forEach { protocol ->
            services.forEach { (contract, instance) ->
                val binding: IMessageHandler = ServiceBinding(contract, instance)
                val requestPipeline = ExtensionPipeline(
                    requestFilters = listOf(LoggingFilter("Server recv req")),
                    responseFilters = listOf(LoggingFilter("Server send res")),
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

