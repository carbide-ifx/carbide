package ifx.protocol.rsocket

import ifx.host.Host
import ifx.host.HostBuilder

/** Creates a host with RSocket on [port]. */
fun Host.Companion.default(
    port: Int = 0,
    name: String = "Service Host",
    configure: HostBuilder.() -> Unit = {},
): Host = Host(name = name) {
    listen(RSocketServerProtocol(), port)
    configure()
}
