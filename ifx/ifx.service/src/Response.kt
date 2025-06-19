package ifx.service

import ifx.service.Response.Failure
import ifx.service.Response.Success
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedClassSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ResponseSerializer::class)
sealed interface Response<out T> {

    @Serializable
    data class Success<out T>(val value: T) : Response<T>

    @Serializable
    data class Failure(val errors: List<ErrorCode>) : Response<Nothing>

    companion object {
        operator fun <T> invoke(vararg errorCode: ErrorCode): Response<T> = Failure(errorCode.toList())
        operator fun <T> invoke(errors: List<ErrorCode>): Response<T> = Failure(errors)
        operator fun <T> invoke(value: T): Response<T> = Success(value)
        operator fun <T> invoke(vararg value: T): Response<List<T>> = Success(value.toList())
        operator fun invoke(): Response<Unit> = Success(Unit)

    }
}

fun <T> Response<T>.getOrElse(provider: Failure.() -> T): T = when (this) {
    is Success -> value
    is Failure -> provider(this)
}

inline fun <T> Response<T>.onFailure(action: (Failure) -> Unit): Response<T> = when (this) {
    is Success -> this
    is Failure -> {
        action(this)
        this
    }
}


@OptIn(InternalSerializationApi::class)
class ResponseSerializer<T>(valueSerializer: KSerializer<T>): KSerializer<Response<T>> {
    private val serializer = SealedClassSerializer(
        Response::class.simpleName!!,
        Response::class,
        arrayOf(Success::class, Failure::class),
        arrayOf(Success.serializer(valueSerializer), Failure.serializer())
    )

    override val descriptor: SerialDescriptor = serializer.descriptor
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Response<T> { return serializer.deserialize(decoder) as Response<T> }
    override fun serialize(encoder: Encoder, value: Response<T>) { serializer.serialize(encoder, value) }
}
