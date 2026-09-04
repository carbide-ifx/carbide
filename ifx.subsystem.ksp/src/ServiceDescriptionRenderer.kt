package ifx.subsystem.ksp

import ifx.rpc.schema.ksp.OperationModel
import ifx.rpc.schema.ksp.ServiceModel
import ifx.rpc.schema.ksp.TypeDeclaration
import ifx.rpc.schema.ksp.TypeRef

/** Renders the canonical compiler schema as runtime `ServiceDescription` code. */
internal class ServiceDescriptionRenderer {
    fun render(service: ServiceModel): String =
        "ServiceDescription(\n" +
            "        name = ${literal(service.name)},\n" +
            "        address = ${literal(service.address)},\n" +
            "        kind = ServiceKind.${service.kind.name},\n" +
            "        operations = listOf(${service.operations.joinToString(",") { "\n            ${operation(it)}" }}\n        ),\n" +
            "        types = listOf(${service.declarations.joinToString(",") { "\n            ${declaration(it)}" }}\n        ),\n" +
            "    )"

    private fun operation(operation: OperationModel): String = "OperationDescription(" +
        "name = ${literal(operation.name)}, " +
        "route = ${literal(operation.route)}, " +
        "parameterName = ${operation.parameterName?.let(::literal) ?: "null"}, " +
        "request = ${typeRef(operation.request)}, " +
        "response = ${typeRef(operation.response)}, " +
        "interaction = InteractionType.${operation.interaction.name})"

    private fun typeRef(type: TypeRef): String = when (type) {
        TypeRef.StringType -> "TypeReference.StringType"
        TypeRef.NumberType -> "TypeReference.NumberType"
        TypeRef.BooleanType -> "TypeReference.BooleanType"
        TypeRef.VoidType -> "TypeReference.VoidType"
        is TypeRef.TypeParameter -> "TypeReference.Parameter(${literal(type.name)})"
        is TypeRef.Named -> "TypeReference.Named(${literal(type.qualifiedName)}, " +
            "listOf(${type.arguments.joinToString { typeRef(it) }}))"
        is TypeRef.ArrayType -> "TypeReference.ArrayType(${typeRef(type.element)})"
        is TypeRef.RecordType -> "TypeReference.RecordType(${typeRef(type.value)})"
        is TypeRef.Nullable -> "TypeReference.Nullable(${typeRef(type.value)})"
    }

    private fun declaration(type: TypeDeclaration): String = when (type) {
        is TypeDeclaration.ObjectType -> "TypeDescription.ObjectType(" +
            "name = ${literal(type.qualifiedName)}, " +
            "typeParameters = ${strings(type.typeParameters)}, " +
            "properties = listOf(${type.properties.joinToString { property ->
                "PropertyDescription(name = ${literal(property.name)}, " +
                    "type = ${typeRef(property.type)}, optional = ${property.optional})"
            }}))"
        is TypeDeclaration.StringUnion -> "TypeDescription.StringUnion(" +
            "name = ${literal(type.qualifiedName)}, " +
            "typeParameters = ${strings(type.typeParameters)}, " +
            "values = ${strings(type.values)})"
        is TypeDeclaration.SealedUnion -> "TypeDescription.SealedUnion(" +
            "name = ${literal(type.qualifiedName)}, " +
            "typeParameters = ${strings(type.typeParameters)}, " +
            "discriminator = ${literal(type.discriminator)}, " +
            "variants = listOf(${type.variants.joinToString { variant ->
                "UnionVariantDescription(serialName = ${literal(variant.serialName)}, " +
                    "type = ${typeRef(variant.type)})"
            }}))"
        is TypeDeclaration.Alias -> "TypeDescription.Alias(" +
            "name = ${literal(type.qualifiedName)}, " +
            "typeParameters = ${strings(type.typeParameters)}, " +
            "target = ${typeRef(type.target)})"
    }

    private fun strings(values: List<String>): String =
        "listOf(${values.joinToString { literal(it) }})"

    private fun literal(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
