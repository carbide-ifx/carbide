package ifx.proxy.factory.jsonrpc

import ifx.host.IHost
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.jsonrpc.JsonRpcClientProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.factory.ProxyFactoryBase

class JsonRpcProxyFactory private constructor(
    private val delegate: ProxyFactoryBase,
) : IProxyFactory by delegate {
    constructor(
        port: Int,
        host: String = "localhost",
    ) : this(JsonRpcClientProtocol(host, port))

    private constructor(
        protocol: IClientProtocol,
    ) : this(ProxyFactoryBase(protocol))

    companion object {
        fun forHost(host: IHost): JsonRpcProxyFactory =
            JsonRpcProxyFactory(
                JsonRpcClientProtocol(baseUrl = { "http://localhost:${host.port(JSON_RPC_PROTOCOL_ID)}" }),
            )
                .apply { addInterceptors(host.interceptors) }
    }
}
