package ifx.subsystem

import ifx.host.Host
import ifx.host.HostBuilder
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.protocol.rsocket.RSocketServerProtocol

/**
 * Creates a host with RSocket on [port] using an explicit descriptor registry.
 *
 * The RPC processor generates a registry-free overload for normal subsystem code.
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
