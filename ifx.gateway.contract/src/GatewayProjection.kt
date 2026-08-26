package ifx.gateway.contract

import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.OperationDescriptor
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService

/** Immutable, transport-neutral selection of existing RPC operations for one client surface. */
interface GatewayProjection {
    val name: String
    val version: Int?
    val services: List<GatewayServiceProjection>
}

/** Build-time index generated for the gateway projections declared by one source module. */
interface GatewayProjectionProvider {
    val projections: List<GatewayProjection>
}

data class GatewayServiceProjection(
    val name: String,
    val descriptor: ServiceDescriptor<*>,
    val operations: List<GatewayOperationProjection>,
)

data class GatewayOperationProjection(
    val name: String,
    val description: OperationDescription,
)

data class GatewayOperationSelection<Service : IService>(
    val operation: OperationDescriptor<Service, *, *>,
    val name: String,
)

/** Overrides the public operation name without replacing its typed manager operation identity. */
fun <Service : IService, Request, Response> OperationDescriptor<Service, Request, Response>.named(
    name: String,
): GatewayOperationSelection<Service> {
    require(name.isNotBlank()) { "Gateway operation name cannot be blank" }
    require('/' !in name) { "Gateway operation name cannot contain /" }
    return GatewayOperationSelection(this, name)
}

fun gateway(
    name: String,
    version: Int? = null,
    block: GatewayProjectionBuilder.() -> Unit,
): GatewayProjection {
    require(name.isNotBlank()) { "Gateway projection name cannot be blank" }
    require('/' !in name) { "Gateway projection name cannot contain /" }
    require(version == null || version > 0) { "Gateway projection version must be positive" }
    return GatewayProjectionBuilder(name, version).apply(block).build()
}

@GatewayProjectionDsl
class GatewayProjectionBuilder internal constructor(
    private val name: String,
    private val version: Int?,
) {
    private val services = mutableListOf<GatewayServiceProjection>()
    private var selecting: ServiceDescriptor<*>? = null
    private var selectedOperations: List<SelectedOperation>? = null

    fun <Service : IService, Descriptor : ServiceDescriptor<Service>> expose(
        descriptor: Descriptor,
        asName: String? = null,
        selection: (Descriptor.() -> Unit)? = null,
    ) {
        check(selecting == null) { "Gateway service projections cannot be nested" }
        selecting = descriptor
        selectedOperations = null
        try {
            selection?.invoke(descriptor)
            val operations = selectedOperations ?: descriptor.description.operations
                .map { operation -> SelectedOperation(
                    serviceAddress = descriptor.address,
                    description = operation,
                    name = operation.name,
                ) }
            require(operations.isNotEmpty()) {
                "Gateway service ${descriptor.description.name} exposes no operations"
            }
            val publicName = asName ?: conventionalServiceName(descriptor.description.name)
            require(publicName.isNotBlank()) { "Gateway service name cannot be blank" }
            require('/' !in publicName) { "Gateway service name cannot contain /" }
            require(services.none { service -> service.name == publicName }) {
                "Duplicate gateway service name: $publicName"
            }
            val duplicateOperation = operations.groupingBy(SelectedOperation::name)
                .eachCount()
                .entries
                .firstOrNull { entry -> entry.value > 1 }
            require(duplicateOperation == null) {
                "Duplicate gateway operation name in $publicName: ${duplicateOperation?.key}"
            }
            services += GatewayServiceProjection(
                name = publicName,
                descriptor = descriptor,
                operations = operations.map { operation ->
                    GatewayOperationProjection(operation.name, operation.description)
                },
            )
        } finally {
            selecting = null
            selectedOperations = null
        }
    }

    fun <Service : IService> only(
        vararg operations: OperationDescriptor<Service, *, *>,
    ) = select(operations.map { operation -> operation.selected(operation.description.name) })

    fun <Service : IService> only(
        vararg operations: GatewayOperationSelection<Service>,
    ) = select(operations.map { operation -> operation.operation.selected(operation.name) })

    private fun select(
        operations: List<SelectedOperation>,
    ) {
        val descriptor = checkNotNull(selecting) { "only(...) must be called inside expose(...)" }
        require(operations.isNotEmpty()) { "only(...) requires at least one operation" }
        operations.forEach { operation ->
            require(operation.serviceAddress == descriptor.address) {
                "Operation ${operation.description.name} belongs to ${operation.serviceAddress}, not ${descriptor.address}"
            }
        }
        val duplicate = operations.groupingBy(SelectedOperation::name)
            .eachCount()
            .entries
            .firstOrNull { entry -> entry.value > 1 }
        require(duplicate == null) { "Duplicate gateway operation: ${duplicate?.key}" }
        check(selectedOperations == null) { "only(...) may be called once per exposed service" }
        selectedOperations = operations
    }

    internal fun build(): GatewayProjection {
        require(services.isNotEmpty()) { "Gateway projection $name exposes no services" }
        val immutableServices = services.toList()
        return object : GatewayProjection {
            override val name: String = this@GatewayProjectionBuilder.name
            override val version: Int? = this@GatewayProjectionBuilder.version
            override val services: List<GatewayServiceProjection> = immutableServices
        }
    }

    private fun conventionalServiceName(contractName: String): String {
        val withoutInterfacePrefix = contractName.dropInterfacePrefix()
        val withoutManagerSuffix = withoutInterfacePrefix.removeSuffix("Manager")
        return withoutManagerSuffix.replaceFirstChar(Char::lowercaseChar)
    }

    private fun String.dropInterfacePrefix(): String =
        if (length > 1 && first() == 'I' && this[1].isUpperCase()) drop(1) else this

}

private data class SelectedOperation(
    val serviceAddress: String,
    val description: OperationDescription,
    val name: String,
)

private fun OperationDescriptor<*, *, *>.selected(name: String): SelectedOperation = SelectedOperation(
    serviceAddress = serviceAddress,
    description = description,
    name = name,
)

@DslMarker
annotation class GatewayProjectionDsl
