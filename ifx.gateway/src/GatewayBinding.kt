package ifx.gateway

import ifx.gateway.contract.GatewayOperationProjection
import ifx.gateway.contract.GatewayProjection
import ifx.gateway.contract.GatewayFailure
import ifx.gateway.contract.GatewayFailureException
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.TypeDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

fun GatewayProjection.bind(
    resolve: (ServiceDescriptor<*>) -> IBinding?,
): Endpoint {
    val routes = services.flatMap { service ->
        val target = requireNotNull(resolve(service.descriptor)) {
            "No gateway target for ${service.descriptor.address}"
        }
        service.operations.map { operation ->
            val publicRoute = "${service.name}/${operation.name}"
            publicRoute to GatewayRoute(operation, target)
        }
    }.also { entries ->
        val duplicate = entries.groupingBy(Pair<String, GatewayRoute>::first)
            .eachCount()
            .entries
            .firstOrNull { entry -> entry.value > 1 }
        require(duplicate == null) { "Duplicate gateway route: ${duplicate?.key}" }
    }.toMap()

    val address = version?.let { "$name/v$it" } ?: name
    return Endpoint(
        address = address,
        binding = GatewayBinding(routes),
        description = ServiceDescription(
            name = name,
            address = address,
            kind = ServiceKind.SERVICE,
            operations = routes.map { (publicRoute, route) ->
                route.operation.description.toPublicDescription(publicRoute, route.operation.name)
            },
            types = mergedTypes(),
        ),
    )
}

private fun GatewayProjection.mergedTypes(): List<TypeDescription> {
    val types = mutableMapOf<String, TypeDescription>()
    services.forEach { service ->
        service.descriptor.description.types.forEach { type ->
            val existing = types[type.name]
            require(existing == null || existing == type) {
                "Incompatible gateway type descriptions for ${type.name}"
            }
            types[type.name] = type
        }
    }
    return types.values.sortedBy { type -> type.name }
}

private fun OperationDescription.toPublicDescription(
    publicRoute: String,
    publicName: String,
): OperationDescription = copy(name = publicName, route = publicRoute)

private data class GatewayRoute(
    val operation: GatewayOperationProjection,
    val target: IBinding,
)

private class GatewayBinding(
    private val routes: Map<String, GatewayRoute>,
) : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) {
        val route = route(operation, InteractionType.FIRE_AND_FORGET)
        safely { route.target.fireAndForget(route.operation.description.route, message) }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        val route = route(operation, InteractionType.REQUEST_RESPONSE)
        return safely { route.target.requestResponse(route.operation.description.route, message) }
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        val route = route(operation, InteractionType.REQUEST_STREAM)
        return flow {
            safely { emitAll(route.target.requestStream(route.operation.description.route, message)) }
        }
    }

    private fun route(publicRoute: String, interaction: InteractionType): GatewayRoute {
        val route = routes[publicRoute] ?: throw GatewayOperationNotExposedException(publicRoute)
        if (route.operation.description.interaction != interaction) {
            throw GatewayInteractionException(
                publicRoute = publicRoute,
                expected = route.operation.description.interaction,
                actual = interaction,
            )
        }
        return route
    }

    private suspend fun <Result> safely(block: suspend () -> Result): Result = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: GatewayFailureException) {
        throw failure
    } catch (failure: Throwable) {
        throw GatewayFailureException(
            GatewayFailure("internal_error", "The request could not be completed"),
            failure,
        )
    }
}

class GatewayOperationNotExposedException(
    val publicRoute: String,
) : GatewayFailureException(
    GatewayFailure("operation_not_found", "Gateway operation is not exposed", mapOf("operation" to publicRoute)),
)

class GatewayInteractionException(
    val publicRoute: String,
    val expected: InteractionType,
    val actual: InteractionType,
) : GatewayFailureException(
    GatewayFailure("invalid_interaction", "Gateway operation uses a different interaction type"),
)
