package ifx.protocol.contract.interceptors

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Reports exceptions that escape an RPC invocation, then preserves the original failure. */
class UnhandledExceptionInterceptor(
    private val onException: (InterceptorCall, Throwable) -> Unit,
) : IInterceptor {
    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        try {
            emitAll(next(call))
        } catch (exception: Throwable) {
            if (exception is CancellationException) throw exception

            try {
                onException(call, exception)
            } catch (_: Throwable) {
                // Exception reporting must not replace the original RPC failure.
            }
            throw exception
        }
    }
}
