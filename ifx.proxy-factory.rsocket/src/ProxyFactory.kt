package ifx.proxy.factory

import ifx.host.IHost
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.rsocket.DEFAULT_KEEP_ALIVE
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.proxy.contract.IProxyFactory
import io.rsocket.kotlin.keepalive.KeepAlive

/**
 * [keepAlive] governs how quickly a lost connection is noticed: calls are not bounded by a
 * client-side timeout, so a peer that stops responding is only detected once the keep-alive
 * lifetime expires. Tighten it for latency-sensitive callers.
 */
class RSocketProxyFactory private constructor(
    private val delegate: ProxyFactoryBase,
) : IProxyFactory by delegate {
    constructor(
        port: Int,
        host: String = "localhost",
        keepAlive: KeepAlive = DEFAULT_KEEP_ALIVE,
    ) : this(RSocketClientProtocol(baseUrl = { "ws://$host:$port" }, keepAlive = keepAlive))

    private constructor(
        protocol: IClientProtocol,
    ) : this(ProxyFactoryBase(protocol))

    companion object {
        fun forHost(
            host: IHost,
            keepAlive: KeepAlive = DEFAULT_KEEP_ALIVE,
        ): RSocketProxyFactory =
            RSocketProxyFactory(
                RSocketClientProtocol(
                    baseUrl = { "ws://localhost:${host.port(RSOCKET_PROTOCOL_ID)}" },
                    keepAlive = keepAlive,
                ),
            )
                .apply { addInterceptors(host.interceptors) }
    }
}

typealias ProxyFactory = RSocketProxyFactory
