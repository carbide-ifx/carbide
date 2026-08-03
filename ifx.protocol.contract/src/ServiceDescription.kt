package ifx.protocol.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Runtime-safe service metadata used by generated clients and development tooling. */
@Serializable
data class ServiceDescription(
    val name: String,
    val address: String,
    val operations: List<OperationDescription>,
    val types: List<TypeDescription>,
)

@Serializable
data class ServiceCatalog(
    val name: String,
    val services: List<ServiceDescription>,
)

@Serializable
data class OperationDescription(
    val name: String,
    val route: String,
    val parameterName: String?,
    val request: TypeReference,
    val response: TypeReference,
    val interaction: InteractionType,
)

@Serializable
sealed interface TypeReference {
    @Serializable
    @SerialName("string")
    data object StringType : TypeReference

    @Serializable
    @SerialName("number")
    data object NumberType : TypeReference

    @Serializable
    @SerialName("boolean")
    data object BooleanType : TypeReference

    @Serializable
    @SerialName("void")
    data object VoidType : TypeReference

    @Serializable
    @SerialName("parameter")
    data class Parameter(val name: String) : TypeReference

    @Serializable
    @SerialName("named")
    data class Named(val name: String, val arguments: List<TypeReference> = emptyList()) : TypeReference

    @Serializable
    @SerialName("array")
    data class ArrayType(val element: TypeReference) : TypeReference

    @Serializable
    @SerialName("record")
    data class RecordType(val value: TypeReference) : TypeReference

    @Serializable
    @SerialName("nullable")
    data class Nullable(val value: TypeReference) : TypeReference
}

@Serializable
sealed interface TypeDescription {
    val name: String
    val typeParameters: List<String>

    @Serializable
    @SerialName("object")
    data class ObjectType(
        override val name: String,
        override val typeParameters: List<String>,
        val properties: List<PropertyDescription>,
    ) : TypeDescription

    @Serializable
    @SerialName("stringUnion")
    data class StringUnion(
        override val name: String,
        override val typeParameters: List<String> = emptyList(),
        val values: List<String>,
    ) : TypeDescription

    @Serializable
    @SerialName("sealedUnion")
    data class SealedUnion(
        override val name: String,
        override val typeParameters: List<String>,
        val discriminator: String,
        val variants: List<UnionVariantDescription>,
    ) : TypeDescription

    @Serializable
    @SerialName("alias")
    data class Alias(
        override val name: String,
        override val typeParameters: List<String>,
        val target: TypeReference,
    ) : TypeDescription
}

@Serializable
data class PropertyDescription(
    val name: String,
    val type: TypeReference,
    val optional: Boolean,
)

@Serializable
data class UnionVariantDescription(
    val serialName: String,
    val type: TypeReference.Named,
)
