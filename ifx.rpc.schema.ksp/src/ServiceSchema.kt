package ifx.rpc.schema.ksp

data class ServiceModel(
    val name: String,
    val address: String,
    val kind: ServiceKind,
    val operations: List<OperationModel>,
    val declarations: List<TypeDeclaration>,
)

enum class ServiceKind {
    SERVICE,
    UTILITY,
}

data class OperationModel(
    val name: String,
    val route: String,
    val parameterName: String?,
    val request: TypeRef,
    val response: TypeRef,
    val interaction: Interaction,
)

enum class Interaction {
    FIRE_AND_FORGET,
    REQUEST_RESPONSE,
    REQUEST_STREAM,
}

sealed interface TypeRef {
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

sealed interface TypeDeclaration {
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

data class PropertyModel(
    val name: String,
    val type: TypeRef,
    val optional: Boolean,
)

data class SealedVariant(
    val serialName: String,
    val type: TypeRef.Named,
)
