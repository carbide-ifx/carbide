package ifx.proxy.factory

import ifx.protocol.contract.IProtocolServer
import ifx.protocol.contract.IProtocolServer.Companion.createClient
import ifx.protocol.rsocket.RSocketEndpoint
import ifx.service.IService
import java.lang.reflect.Proxy

class ProxyFactory(protocol: IProtocolServer? = null) {
    val protocol: IProtocolServer = protocol ?: RSocketEndpoint()
    inline fun <reified T : IService> create(instance: T? = null): T {
        val contract = T::class.java
        val interfaces = if (contract.isInterface) arrayOf(contract) else contract.interfaces
        return Proxy.newProxyInstance(
            contract.classLoader,
            interfaces,
            instance?.let { InstanceHandler(it) }
                ?: EndpointInvocationHandler(protocol.createClient<T>())
        ) as T
    }

}

