package ifx.protocol.contract

import ifx.context.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.single

/** Executes pre-ordered interceptor onion layers around [nextBinding]. */
sealed class InterceptorPipeline protected constructor(
    interceptors: List<IInterceptor>,
    private val nextBinding: IBinding,
) : IBinding {
    private val chain: InterceptorChain = interceptors
        .foldRight(InterceptorChain(::invokeBinding)) { interceptor, next ->
            InterceptorChain { call -> interceptor.intercept(call, next) }
        }

    protected abstract fun createCall(
        interactionType: InteractionType,
        operation: String,
        message: Message,
    ): InterceptorCall

    override suspend fun fireAndForget(operation: String, message: Message) {
        invoke(InteractionType.FIRE_AND_FORGET, operation, message).collect()
    }

    override suspend fun requestResponse(operation: String, message: Message): Message =
        invoke(InteractionType.REQUEST_RESPONSE, operation, message).single()

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
        invoke(InteractionType.REQUEST_STREAM, operation, message)

    private fun invoke(
        interactionType: InteractionType,
        operation: String,
        message: Message,
    ): Flow<Message> = chain(createCall(interactionType, operation, message))

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

/** Client calls enter interceptors in registration order. */
class ClientInterceptorPipeline(
    private val service: String,
    interceptors: List<IInterceptor> = emptyList(),
    nextBinding: IBinding,
) : InterceptorPipeline(listOf(ContextPropagationInterceptor) + interceptors, nextBinding) {
    override fun createCall(
        interactionType: InteractionType,
        operation: String,
        message: Message,
    ): ClientCall = ClientCall(service, interactionType, operation, message)
}

/**
 * Server calls enter interceptors in reverse registration order, making a
 * shared client/server interceptor list symmetrical across the transport.
 */
class ServerInterceptorPipeline(
    private val service: String,
    interceptors: List<IInterceptor> = emptyList(),
    nextBinding: IBinding,
) : InterceptorPipeline(interceptors.asReversed() + ContextPropagationInterceptor, nextBinding) {
    override fun createCall(
        interactionType: InteractionType,
        operation: String,
        message: Message,
    ): ServerCall = ServerCall(service, interactionType, operation, message)
}

/**
 * Injects the caller's Context before client interceptors and extracts it after
 * server interceptors have decoded the incoming message.
 */
private object ContextPropagationInterceptor : IInterceptor {
    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val context = when (call) {
            is ClientCall -> Context.currentOrNull() ?: call.message.contextOrNull() ?: Context()
            is ServerCall -> call.message.context()
        }
        val message = when (call) {
            is ClientCall -> call.message.withContext(context)
            is ServerCall -> call.message
        }

        emitAll(next(call.withMessage(message)).flowOn(context))
    }
}
