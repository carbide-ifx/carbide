package ifx.protocol.rsocket

import ifx.host.Host
import ifx.host.HostBuilder
import ifx.protocol.contract.PlatformServiceDescriptorRegistry
import ifx.protocol.contract.ServiceDescriptorRegistry

/** Creates a host with RSocket on [port]. */
fun Host.Companion.default(
    port: Int = 0,
    name: String = "Service Host",
    serviceDescriptors: ServiceDescriptorRegistry = PlatformServiceDescriptorRegistry,
    configure: HostBuilder.() -> Unit = {},
): Host = Host(name = name, serviceDescriptors = serviceDescriptors) {
    listen(RSocketServerProtocol(), port)
    configure()
}
