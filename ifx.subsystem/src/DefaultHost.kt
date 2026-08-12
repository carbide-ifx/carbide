package ifx.subsystem

import ifx.host.Host
import ifx.host.HostBuilder
import ifx.protocol.rsocket.RSocketServerProtocol

/** Creates a host with RSocket on [port]. */
fun Host.Companion.default(
    port: Int = 0,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit = {},
): Host = Host(name = name) {
    listen(RSocketServerProtocol(), port)
    configure()
}
