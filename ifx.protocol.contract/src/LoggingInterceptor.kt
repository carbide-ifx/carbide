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
            is ClientCall -> "Proxy -> " to "Proxy <- "
            is ServerCall -> "-> " to "<- "
        }
        val callName = "${call.service.substringAfterLast('.')}.${call.operation}"

        log.info { "$requestDirection $callName: ${call.message}" }
        next(call).collect { message ->
            log.info { "$responseDirection $callName: $message" }
            emit(message)
        }
    }
}
