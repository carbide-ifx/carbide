package ifx.proxy.factory.jsonrpc

import ifx.host.IHost
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.PlatformServiceDescriptorRegistry
import ifx.protocol.contract.ServiceDescriptorRegistry
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
        serviceDescriptors: ServiceDescriptorRegistry = PlatformServiceDescriptorRegistry,
    ) : this(JsonRpcClientProtocol(host, port), serviceDescriptors)

    private constructor(
        protocol: IClientProtocol,
        serviceDescriptors: ServiceDescriptorRegistry,
    ) : this(ProxyFactoryBase(protocol, serviceDescriptors))

    companion object {
        fun forHost(host: IHost): JsonRpcProxyFactory =
            JsonRpcProxyFactory(
                JsonRpcClientProtocol(baseUrl = { "http://localhost:${host.port(JSON_RPC_PROTOCOL_ID)}" }),
                host.serviceDescriptors,
            )
                .apply { addInterceptors(host.interceptors) }
    }
}
