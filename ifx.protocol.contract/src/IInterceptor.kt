package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow

@kotlinx.serialization.Serializable
enum class InteractionType {
    @kotlinx.serialization.SerialName("fireAndForget")
    FIRE_AND_FORGET,
    @kotlinx.serialization.SerialName("requestResponse")
    REQUEST_RESPONSE,
    @kotlinx.serialization.SerialName("requestStream")
    REQUEST_STREAM,
}

sealed interface InterceptorCall {
    /** Fully qualified service descriptor address. */
    val service: String
    val interactionType: InteractionType
    val operation: String
    val message: Message

    fun withMessage(message: Message): InterceptorCall
}

data class ClientCall(
    override val service: String,
    override val interactionType: InteractionType,
    override val operation: String,
    override val message: Message,
) : InterceptorCall {
    override fun withMessage(message: Message): ClientCall = copy(message = message)
}

data class ServerCall(
    override val service: String,
    override val interactionType: InteractionType,
    override val operation: String,
    override val message: Message,
) : InterceptorCall {
    override fun withMessage(message: Message): ServerCall = copy(message = message)
}

fun interface InterceptorChain {
    operator fun invoke(call: InterceptorCall): Flow<Message>
}

/**
 * An around-call layer in the RPC pipeline.
 *
 * The returned flow represents the complete invocation: it emits no messages for
 * fire-and-forget, one for request/response, and any number for request streams.
 * Interceptors should use a `flow` block and collect [next] inside it when work
 * must surround the complete invocation, including stream collection.
 */
fun interface IInterceptor {
    fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message>
}
