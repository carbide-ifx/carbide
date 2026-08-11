package test.service.aggregation

import ifx.generated.TestServiceAggregationServiceDescriptors
import ifx.host.Host
import ifx.protocol.contract.ServiceDescriptorRegistry
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.subsystem.default
import ifx.subsystem.subsystem

/** Compile-time proof that dependency contracts are aggregated for Native. */
val dependencyServiceDescriptors: ServiceDescriptorRegistry =
    TestServiceAggregationServiceDescriptors

/** Compile-time proof that generated subsystem host conveniences are available for Native. */
fun defaultSubsystemHost(): Host = Host.default()

fun configuredSubsystemHost(): Host = Host.subsystem {
    listen(RSocketServerProtocol())
}
