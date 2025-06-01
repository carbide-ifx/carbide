package ifx.service

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedClassSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with= ResponseSerializer::class)
sealed interface Response<out T> {

    @Serializable
    data class Success<out T>(val value: T) : Response<T>

    @Serializable
    data class Failure(val errors: List<ErrorCode>) : Response<Nothing>

    companion object {
        operator fun invoke() = Success(Unit)
        fun <T : Any> emptyList() = Success(listOf<T>())
        operator fun <T : Any> invoke(value: T) = Success(value)
        fun fail(vararg errorCodes: ErrorCode) = Failure(errorCodes.toList())
        fun fail(errorCodes: List<ErrorCode>) = Failure(errorCodes)
    }
}

@OptIn(InternalSerializationApi::class)
class ResponseSerializer<T>(valueSerializer: KSerializer<T>): KSerializer<Response<T>> {
    private val serializer = SealedClassSerializer(
        Response::class.simpleName!!,
        Response::class,
        arrayOf(Response.Success::class, Response.Failure::class),
        arrayOf(Response.Success.serializer(valueSerializer), Response.Failure.serializer())
    )

    override val descriptor: SerialDescriptor = serializer.descriptor
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Response.Success<T> { return serializer.deserialize(decoder) as Response.Success<T> }
    override fun serialize(encoder: Encoder, value: Response<T>) { serializer.serialize(encoder, value) }
}
