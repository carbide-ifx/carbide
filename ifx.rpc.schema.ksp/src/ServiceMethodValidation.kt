package ifx.rpc.schema.ksp

internal data class ServiceMethodShape(
    val name: String,
    val parameterCount: Int,
    val hasTypeParameters: Boolean,
    val isSuspending: Boolean,
    val returnsFlow: Boolean,
    val returnsUnit: Boolean,
    val isFireAndForget: Boolean,
)

internal data class ServiceMethodDiagnostic(
    val methodIndex: Int,
    val message: String,
)

internal fun validateServiceMethods(methods: List<ServiceMethodShape>): List<ServiceMethodDiagnostic> = buildList {
    methods.forEachIndexed { index, method ->
        val validShape = !method.hasTypeParameters && method.parameterCount <= 1 &&
            ((method.returnsFlow && !method.isSuspending) || (!method.returnsFlow && method.isSuspending))
        if (!validShape) {
            add(
                ServiceMethodDiagnostic(
                    index,
                    "IFX service method ${method.name} must be a suspend unary/Unit method or a " +
                        "non-suspending Flow method, with at most one parameter and no type parameters.",
                ),
            )
        }
        if (method.isFireAndForget && (!method.returnsUnit || !method.isSuspending)) {
            add(
                ServiceMethodDiagnostic(
                    index,
                    "@FireAndForget may only be used on suspending IFX service methods returning Unit.",
                ),
            )
        }
    }
    methods.groupBy(ServiceMethodShape::name)
        .filterValues { overloads -> overloads.size > 1 }
        .forEach { (name, _) ->
            add(
                ServiceMethodDiagnostic(
                    methods.indexOfFirst { it.name == name },
                    "IFX service method overloads are not supported: $name. Use distinct operation names.",
                ),
            )
        }
}
