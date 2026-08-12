package ifx.subsystem

import ifx.host.Host
import ifx.host.HostBuilder

/** Creates a host using the services reachable from the current subsystem module. */
fun Host.Companion.subsystem(
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit,
): Host = Host(
    name = name,
    configure = configure,
)
