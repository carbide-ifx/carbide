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

/**
 * Which side of the transport an invocation is passing through.
 *
 * Interceptors are registered once and installed on both sides, so a layer that must behave
 * asymmetrically — encrypt outbound, decrypt inbound — branches on this.
 */
enum class CallDirection {
    CLIENT,
    SERVER,
}

data class InterceptorCall(
    val direction: CallDirection,
    /** Fully qualified service descriptor address. */
    val service: String,
    val interactionType: InteractionType,
    val operation: String,
    val message: Message,
) {
    val isClient: Boolean get() = direction == CallDirection.CLIENT
    val isServer: Boolean get() = direction == CallDirection.SERVER
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
