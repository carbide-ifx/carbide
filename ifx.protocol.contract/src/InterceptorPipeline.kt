package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single

/**
 * Executes interceptor onion layers around [nextBinding].
 *
 * Client calls enter interceptors in registration order and server calls in reverse, so one shared
 * interceptor list produces a symmetric onion across the transport.
 */
class InterceptorPipeline(
    private val service: String,
    private val direction: CallDirection,
    interceptors: List<IInterceptor> = emptyList(),
    private val nextBinding: IBinding,
) : IBinding {
    private val chain: InterceptorChain = interceptors
        .let { if (direction == CallDirection.SERVER) it.asReversed() else it }
        .foldRight(InterceptorChain(::invokeBinding)) { interceptor, next ->
            InterceptorChain { call -> interceptor.intercept(call, next) }
        }

    override suspend fun fireAndForget(operation: String, message: Message) {
        invoke(InteractionType.FIRE_AND_FORGET, operation, message).collect()
    }

    override suspend fun requestResponse(operation: String, message: Message): Message =
        invoke(InteractionType.REQUEST_RESPONSE, operation, message).single()

    override fun requestStream(operation: String, message: Message): Flow<Message> =
        invoke(InteractionType.REQUEST_STREAM, operation, message)

    private fun invoke(
        interactionType: InteractionType,
        operation: String,
        message: Message,
    ): Flow<Message> = chain(InterceptorCall(direction, service, interactionType, operation, message))

    private fun invokeBinding(call: InterceptorCall): Flow<Message> = flow {
        when (call.interactionType) {
            InteractionType.FIRE_AND_FORGET ->
                nextBinding.fireAndForget(call.operation, call.message)

            InteractionType.REQUEST_RESPONSE ->
                emit(nextBinding.requestResponse(call.operation, call.message))

            InteractionType.REQUEST_STREAM ->
                emitAll(nextBinding.requestStream(call.operation, call.message))
        }
    }
}
