package ifx.host.rsocket

import ifx.host.HostBase
import ifx.host.IHost
import ifx.protocol.rsocket.RSocketProtocol

class Host(
    val port: Int,
    val name: String = "Service Host",
) : IHost by HostBase(RSocketProtocol(port), name)
