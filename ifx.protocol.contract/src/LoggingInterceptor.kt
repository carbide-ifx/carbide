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

class LoggingInterceptor(val prefix: String) : IInterceptor {
    val log = Log("LoggingInterceptor")

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        val (requestDirection, responseDirection) = when (call) {
            is ClientCall -> "Client send" to "Client receive"
            is ServerCall -> "Server receive" to "Server send"
        }

        log.info { "$requestDirection: $prefix ${call.operation}: ${call.message}" }
        next(call).collect { message ->
            log.info { "HELLO SIR" }
            log.info { "$responseDirection: $prefix ${call.operation}: $message" }
            emit(message)
        }
    }
}
