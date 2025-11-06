package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.IProxyFactory

class ProxyFactory(val port: Int) : IProxyFactory by ProxyFactoryBase(RSocketProtocol(port)) {
    companion object {
        fun forHost(host: IHost): IProxyFactory = ProxyFactoryBase(host.protocol)
    }
}




