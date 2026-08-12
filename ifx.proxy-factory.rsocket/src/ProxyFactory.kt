package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.proxy.contract.IProxyFactory

class RSocketProxyFactory private constructor(
    private val delegate: ProxyFactoryBase,
) : IProxyFactory by delegate {
    constructor(
        port: Int,
        host: String = "localhost",
    ) : this(RSocketClientProtocol(host, port))

    private constructor(
        protocol: IClientProtocol,
    ) : this(ProxyFactoryBase(protocol))

    companion object {
        fun forHost(host: IHost): RSocketProxyFactory =
            RSocketProxyFactory(
                RSocketClientProtocol { "ws://localhost:${host.port(RSOCKET_PROTOCOL_ID)}" },
            )
                .apply { addInterceptors(host.interceptors) }
    }
}

typealias ProxyFactory = RSocketProxyFactory
