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
 * Creates a development host with unauthenticated RSocket and JSON-RPC listeners,
 * the actuator service, Kubernetes health endpoints, and the bundled browser-based service
 * explorer. The returned host is not started.
 */
fun Host.Companion.development(
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
        listen(RSocketServerProtocol(), rsocketPort) {
            install(ServiceExplorer())
            install(HealthEndpoints())
        }
        listen(JsonRpcServerProtocol(), jsonRpcPort)
    }
    host.addInterceptors(interceptors)
    host.registerActuator(actuatorDescriptor)
    return host
}

private fun missingActuatorDescriptor(): Nothing = error(
    "Host.development() requires the ifx.rpc.compiler plugin or an explicit IActuator descriptor",
)
