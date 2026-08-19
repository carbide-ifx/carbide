package ifx.service

import ifx.service.Response.Failure
import ifx.service.Response.Success
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

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


class ResponseSerializer<T>(private val valueSerializer: KSerializer<T>): KSerializer<Response<T>> {
    private val errorListSerializer = ListSerializer(SerializedErrorCode.serializer())
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ifx.service.Response") {
        element<String>("type")
        element("value", valueSerializer.descriptor, isOptional = true)
        element("errors", errorListSerializer.descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): Response<T> = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var value: T? = null
        var hasValue = false
        var errors: List<SerializedErrorCode>? = null
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> type = decodeStringElement(descriptor, index)
                1 -> {
                    value = decodeSerializableElement(descriptor, index, valueSerializer)
                    hasValue = true
                }
                2 -> errors = decodeSerializableElement(descriptor, index, errorListSerializer)
                else -> throw SerializationException("Unexpected Response field index $index")
            }
        }
        when (type) {
            SUCCESS_TYPE -> {
                if (!hasValue) throw SerializationException("Response.Success is missing value")
                @Suppress("UNCHECKED_CAST")
                Success(value as T)
            }
            FAILURE_TYPE -> Failure(errors ?: throw SerializationException("Response.Failure is missing errors"))
            else -> throw SerializationException("Unknown Response type $type")
        }
    }

    override fun serialize(encoder: Encoder, value: Response<T>) {
        encoder.encodeStructure(descriptor) {
            when (value) {
                is Success -> {
                    encodeStringElement(descriptor, 0, SUCCESS_TYPE)
                    encodeSerializableElement(descriptor, 1, valueSerializer, value.value)
                }
                is Failure -> {
                    encodeStringElement(descriptor, 0, FAILURE_TYPE)
                    encodeSerializableElement(
                        descriptor,
                        2,
                        errorListSerializer,
                        value.errors.map { SerializedErrorCode(it.code, it.message) },
                    )
                }
            }
        }
    }

    private companion object {
        const val SUCCESS_TYPE = "ifx.service.Response.Success"
        const val FAILURE_TYPE = "ifx.service.Response.Failure"
    }
}

@Serializable
private data class SerializedErrorCode(
    override val code: String,
    override val message: String,
) : ErrorCode
