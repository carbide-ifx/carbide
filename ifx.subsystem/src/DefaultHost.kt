package ifx.subsystem

import ifx.actuator.IActuator
import ifx.actuator.registerActuator
import ifx.host.Host
import ifx.service.explorer.ServiceExplorer
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSocketServerProtocol

/**
 * Creates the standard subsystem host with RSocket, JSON-RPC,
 * and the actuator service. When [serviceExplorerDirectory] is supplied, the host also serves the
 * browser-based service explorer from that directory. The returned host is not opened.
 */
suspend fun Host.Companion.default(
    name: String = "Service Host",
    rsocketPort: Int = 0,
    jsonRpcPort: Int = 0,
    interceptors: List<IInterceptor> = emptyList(),
    serviceExplorerDirectory: String? = null,
    actuatorDescriptor: ServiceDescriptor<IActuator> = missingActuatorDescriptor(),
): Host {
    val host = Host(name = name) {
        val rsocket = listen(RSocketServerProtocol(), rsocketPort)
        listen(JsonRpcServerProtocol(), jsonRpcPort)
        serviceExplorerDirectory?.let { install(ServiceExplorer(rsocket, it)) }
    }
    host.addInterceptors(interceptors)
    host.registerActuator(actuatorDescriptor)
    return host
}

private fun missingActuatorDescriptor(): Nothing = error(
    "Host.default() requires the ifx.rpc.compiler plugin or an explicit IActuator descriptor",
)
