package ifx.proxy

import ifx.protocol.IProtocol
import ifx.protocol.createHandler
import ifx.protocol.rsocket.RSocketProtocol
import ifx.service.IService
import java.lang.reflect.Proxy

class DynamicProxy(val protocol: IProtocol = RSocketProtocol()) {

    inline fun <reified T : IService> create(instance: T? = null): T {
        val contract = T::class.java
        val interfaces = if (contract.isInterface) arrayOf(contract) else contract.interfaces
        return Proxy.newProxyInstance(
            contract.classLoader,
            interfaces,
            instance
                ?.let { InstanceHandler(it) }
                ?: protocol.createHandler<T>(),
        ) as T
    }

}

