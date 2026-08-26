package ifx.subsystem

import ifx.actuator.HealthEndpoints
import ifx.actuator.IActuator
import ifx.actuator.registerActuator
import ifx.host.Host
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.service.explorer.ServiceExplorer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/**
 * Creates the standard subsystem host with RSocket, JSON-RPC,
 * the actuator service, Kubernetes health endpoints, and the bundled browser-based service
 * explorer. The returned host is not started.
 */
suspend fun Host.Companion.default(
    name: String = "Service Host",
    rsocketPort: Int = 0,
    jsonRpcPort: Int = 0,
    interceptors: List<IInterceptor> = emptyList(),
    actuatorDescriptor: ServiceDescriptor<IActuator> = missingActuatorDescriptor(),
    healthCheckTimeout: Duration = 5.seconds,
    drainDelay: Duration = ZERO,
    requestDrainTimeout: Duration = 20.seconds,
): Host {
    val host = Host(
        name = name,
        healthCheckTimeout = healthCheckTimeout,
        drainDelay = drainDelay,
        requestDrainTimeout = requestDrainTimeout,
    ) {
        val rsocket = listen(RSocketServerProtocol(), rsocketPort)
        listen(JsonRpcServerProtocol(), jsonRpcPort)
        install(ServiceExplorer(rsocket))
        install(HealthEndpoints(rsocket))
    }
    host.addInterceptors(interceptors)
    host.registerActuator(actuatorDescriptor)
    return host
}

private fun missingActuatorDescriptor(): Nothing = error(
    "Host.default() requires the ifx.rpc.compiler plugin or an explicit IActuator descriptor",
)
