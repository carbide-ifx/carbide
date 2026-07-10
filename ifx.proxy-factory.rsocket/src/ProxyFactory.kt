package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.protocol.contract.ServiceRegistry

class ProxyFactory(val port: Int, registry: ServiceRegistry) : IProxyFactory by ProxyFactoryBase(RSocketProtocol(port), registry) {
    companion object {
        fun forHost(host: IHost): IProxyFactory = ProxyFactoryBase(host.protocol, host.registry)
    }
}



