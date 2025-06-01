package ifx.host

import ifx.protocol.contract.IProtocol
import ifx.service.IService
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class Host : IHost {
    private val protocols = mutableListOf<IProtocol>()

    override fun addProtocol(protocol: IProtocol): IHost {
        protocols.add(protocol)
        return this
    }

    override fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        protocols.forEach { it.bind(contract, instance) }
        return this
    }

    override fun <T : IService> registerService(contract: KClass<T>, factory: (CoroutineContext) -> T): IHost {
        require(contract.java.isInterface) {
            "Contract for service must be an interface, but got: ${contract.qualifiedName}"
        }
        TODO("Implement factory-based service registration")
    }


    override fun start(): IHost {
        protocols.forEach { it.start() }
        return this
    }

    override fun stop(): IHost {
        protocols.forEach { it.stop() }
        return this
    }


}
