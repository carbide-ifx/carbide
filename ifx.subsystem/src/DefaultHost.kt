package ifx.subsystem

import ifx.host.Host
import ifx.host.HostBuilder
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.protocol.rsocket.RSocketServerProtocol

/**
 * Creates a host with RSocket on [port] using an explicit descriptor registry.
 *
 * The compiler plugin links the registry-free overload to this function.
 */
fun Host.Companion.default(
    serviceDescriptors: ServiceDescriptorRegistry,
    port: Int = 0,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit = {},
): Host = Host(name = name, serviceDescriptors = serviceDescriptors) {
    listen(RSocketServerProtocol(), port)
    configure()
}

/**
 * Creates the default RSocket host using the generated services reachable from the current subsystem module.
 *
 * The IFX compiler plugin replaces calls to this overload with the registry-taking overload.
 */
fun Host.Companion.default(
    port: Int = 0,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit = {},
): Host = missingIfxCompilerPlugin()
