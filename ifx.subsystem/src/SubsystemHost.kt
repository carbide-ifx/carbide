package ifx.subsystem

import ifx.host.Host
import ifx.host.HostBuilder
import ifx.protocol.contract.ServiceDescriptorRegistry

/** Creates a host using the services reachable from the current subsystem module. */
fun Host.Companion.subsystem(
    serviceDescriptors: ServiceDescriptorRegistry,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit,
): Host = Host(
    name = name,
    serviceDescriptors = serviceDescriptors,
    configure = configure,
)

/**
 * Creates a host using the generated services reachable from the current subsystem module.
 *
 * The IFX compiler plugin replaces calls to this overload with the registry-taking overload.
 */
fun Host.Companion.subsystem(
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit,
): Host = missingIfxCompilerPlugin()

internal fun missingIfxCompilerPlugin(): Nothing = error(
    "The registry-free IFX host API requires the ifx.rpc.compiler plugin in this subsystem module",
)
