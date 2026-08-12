package ifx.rpc.typescript.ksp

internal data class ServiceModel(
    val name: String,
    val address: String,
    val kind: ServiceKind,
    val operations: List<OperationModel>,
    val declarations: List<TypeDeclaration>,
)

internal enum class ServiceKind {
    SERVICE,
    UTILITY,
}

internal data class OperationModel(
    val name: String,
    val typeName: String,
    val route: String,
    val parameterName: String?,
    val request: TypeRef,
    val requestOptional: Boolean,
    val response: TypeRef,
    val interaction: Interaction,
)

internal enum class Interaction {
    FIRE_AND_FORGET,
    REQUEST_RESPONSE,
    REQUEST_STREAM,
}

internal sealed interface TypeRef {
    data object StringType : TypeRef
    data object NumberType : TypeRef
    data object BooleanType : TypeRef
    data object VoidType : TypeRef
    data class TypeParameter(val name: String) : TypeRef
    data class Named(val qualifiedName: String, val arguments: List<TypeRef> = emptyList()) : TypeRef
    data class ArrayType(val element: TypeRef) : TypeRef
    data class RecordType(val value: TypeRef) : TypeRef
    data class Nullable(val value: TypeRef) : TypeRef
}

internal sealed interface TypeDeclaration {
    val qualifiedName: String
    val typeParameters: List<String>

    data class ObjectType(
        override val qualifiedName: String,
        override val typeParameters: List<String>,
        val properties: List<PropertyModel>,
    ) : TypeDeclaration

    data class StringUnion(
        override val qualifiedName: String,
        override val typeParameters: List<String> = emptyList(),
        val values: List<String>,
    ) : TypeDeclaration

    data class SealedUnion(
        override val qualifiedName: String,
        override val typeParameters: List<String>,
        val discriminator: String,
        val variants: List<SealedVariant>,
    ) : TypeDeclaration

    data class Alias(
        override val qualifiedName: String,
        override val typeParameters: List<String>,
        val target: TypeRef,
    ) : TypeDeclaration
}

internal data class PropertyModel(
    val name: String,
    val type: TypeRef,
    val optional: Boolean,
)

internal data class SealedVariant(
    val serialName: String,
    val type: TypeRef.Named,
)
