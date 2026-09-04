package ifx.subsystem.ksp

internal fun operationPropertyNames(
    methodNames: List<String>,
    reservedNames: Set<String>,
): List<String> {
    val allocated = reservedNames.toMutableSet()
    return methodNames.map { name ->
        var propertyName = name
        while (!allocated.add(propertyName)) propertyName += "Operation"
        propertyName
    }
}
