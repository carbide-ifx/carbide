package ifx.host.rsocket

import ifx.host.HostBase
import ifx.host.IHost
import ifx.protocol.rsocket.RSocketProtocol
import ifx.protocol.contract.ServiceRegistry

class Host(
    val port: Int,
    override val registry: ServiceRegistry,
    val name: String = "Service Host",
) : IHost by HostBase(RSocketProtocol(port), registry, name)
