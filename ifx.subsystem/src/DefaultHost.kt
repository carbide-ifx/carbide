package ifx.subsystem

import ifx.actuator.IActuator
import ifx.actuator.registerActuator
import ifx.host.Host
import ifx.host.tooling.ServiceExplorer
import ifx.protocol.contract.ContextInterceptor
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSocketServerProtocol

/**
 * Creates the standard subsystem host with RSocket, JSON-RPC, context propagation,
 * the actuator service, and the browser-based service explorer. The returned host is not opened.
 */
suspend fun Host.Companion.default(
    name: String = "Service Host",
    rsocketPort: Int = 0,
    jsonRpcPort: Int = 0,
    interceptors: List<IInterceptor> = listOf(ContextInterceptor()),
    developmentDirectory: String? = null,
    actuatorDescriptor: ServiceDescriptor<IActuator> = missingActuatorDescriptor(),
): Host {
    val host = Host(name = name) {
        val rsocket = listen(RSocketServerProtocol(), rsocketPort)
        listen(JsonRpcServerProtocol(), jsonRpcPort)
        install(ServiceExplorer(rsocket, developmentDirectory))
    }
    host.addInterceptors(interceptors)
    host.registerActuator(actuatorDescriptor)
    return host
}

private fun missingActuatorDescriptor(): Nothing = error(
    "Host.default() requires the ifx.rpc.compiler plugin or an explicit IActuator descriptor",
)
