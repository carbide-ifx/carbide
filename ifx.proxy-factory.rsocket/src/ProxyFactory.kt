package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.proxy.contract.IProxyFactory

class RSocketProxyFactory private constructor(
    private val delegate: ProxyFactoryBase,
) : IProxyFactory by delegate {
    constructor(
        port: Int,
        serviceDescriptors: ServiceDescriptorRegistry,
        host: String = "localhost",
    ) : this(RSocketClientProtocol(host, port), serviceDescriptors)

    private constructor(
        protocol: IClientProtocol,
        serviceDescriptors: ServiceDescriptorRegistry,
    ) : this(ProxyFactoryBase(protocol, serviceDescriptors))

    companion object {
        fun forHost(host: IHost): RSocketProxyFactory =
            RSocketProxyFactory(
                RSocketClientProtocol { "ws://localhost:${host.port(RSOCKET_PROTOCOL_ID)}" },
                host.serviceDescriptors,
            )
                .apply { addInterceptors(host.interceptors) }
    }
}

typealias ProxyFactory = RSocketProxyFactory
