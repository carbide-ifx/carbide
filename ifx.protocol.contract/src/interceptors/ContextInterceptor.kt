package ifx.protocol.contract.interceptors

import ifx.context.Context
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.protocol.contract.context
import ifx.protocol.contract.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Propagates the ambient [Context] through the reserved context message header. */
class ContextInterceptor : IInterceptor {
    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val context = when (call.direction) {
            CallDirection.CLIENT -> Context.current()
            CallDirection.SERVER -> call.message.context()
        }
        val message = when (call.direction) {
            CallDirection.CLIENT -> call.message.withContext(context.takeUnless(Context::isEmpty))
            CallDirection.SERVER -> call.message
        }

        emitAll(next(call.copy(message = message)).flowOn(context))
    }
}
