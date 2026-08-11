package test.service.aggregation

import ifx.generated.ServiceDescriptors
import ifx.host.Host
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.subsystem.default
import ifx.subsystem.subsystem

/** Compile-time proof that dependency contracts are aggregated for JVM. */
val dependencyServiceDescriptors: ServiceDescriptorRegistry =
    ServiceDescriptors

/** Compile-time proof that stable subsystem host conveniences are available for JVM. */
fun defaultSubsystemHost(): Host = Host.default()

fun configuredSubsystemHost(): Host = Host.subsystem {
    listen(RSocketServerProtocol())
}
