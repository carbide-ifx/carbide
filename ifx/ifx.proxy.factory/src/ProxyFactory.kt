package ifx.proxy

import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.createClient
import ifx.protocol.rsocket.RSocketProtocol
import ifx.service.IService
import java.lang.reflect.Proxy

class ProxyFactory(protocol: IProtocol? = null) {
    val protocol: IProtocol = protocol ?: RSocketProtocol()
    inline fun <reified T : IService> create(instance: T? = null): T {
        val contract = T::class.java
        val interfaces = if (contract.isInterface) arrayOf(contract) else contract.interfaces
        return Proxy.newProxyInstance(
            contract.classLoader,
            interfaces,
            instance?.let { InstanceHandler(it) }
                ?: protocol.createClient<T>(),
        ) as T
    }

}

