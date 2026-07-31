package ifx.protocol.contract.interceptors

import ifx.logging.Log
import ifx.protocol.contract.ClientCall
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServerCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LoggingInterceptor : IInterceptor {
    val log = Log("LoggingInterceptor")

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val (requestDirection, responseDirection) = when (call) {
            is ClientCall -> "Client ->" to "Client <-"
            is ServerCall -> "-> Server" to "<- Server send"
        }

        log.info { "$requestDirection: ${call.operation}: ${call.message}" }
        next(call).collect { message ->
            log.info { "$responseDirection: ${call.operation}: $message" }
            emit(message)
        }
    }
}
