package ifx.gateway

import ifx.gateway.contract.GatewayProjection
import ifx.host.EndpointSource
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService

/**
 * Publishes this projection as one endpoint. Registered services are used by default; standalone
 * gateways can replace individual services with typed remote targets.
 */
fun GatewayProjection.endpointSource(
    configure: GatewayTargetsBuilder.() -> Unit = {},
): EndpointSource {
    val explicitTargets = GatewayTargetsBuilder().apply(configure).build()
    val projectedAddresses = services.map { service -> service.descriptor.address }.toSet()
    val unprojectedTargets = explicitTargets.keys - projectedAddresses
    require(unprojectedTargets.isEmpty()) {
        "Gateway targets are not part of projection $name: ${unprojectedTargets.joinToString()}"
    }
    return EndpointSource { registeredEndpoints ->
        val localTargets = registeredEndpoints
            .groupBy { endpoint -> endpoint.address }
            .mapValues { (address, matches) ->
                require(matches.size == 1) { "Multiple registered endpoints use address $address" }
                matches.single().binding
            }
        listOf(bind { descriptor ->
            explicitTargets[descriptor.address] ?: localTargets[descriptor.address]
        })
    }
}

class GatewayTargetsBuilder internal constructor() {
    private val targets = mutableMapOf<String, IBinding>()

    /** Uses an already-created binding as the target for a typed service contract. */
    fun <Service : IService> target(
        descriptor: ServiceDescriptor<Service>,
        binding: IBinding,
    ) {
        add(descriptor, binding)
    }

    /** Creates a remote target while retaining the service descriptor as the configuration key. */
    fun <Service : IService> remote(
        descriptor: ServiceDescriptor<Service>,
        protocol: IClientProtocol,
        address: String = descriptor.address,
    ) {
        add(descriptor, protocol.createClientBinding(address))
    }

    private fun add(descriptor: ServiceDescriptor<*>, binding: IBinding) {
        require(targets.put(descriptor.address, binding) == null) {
            "Gateway target already configured for ${descriptor.address}"
        }
    }

    internal fun build(): Map<String, IBinding> = targets.toMap()
}
