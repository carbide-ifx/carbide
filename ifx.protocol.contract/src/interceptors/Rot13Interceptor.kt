package ifx.protocol.contract.interceptors

import ifx.protocol.contract.ClientCall
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServerCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Deprecated("Only use for the lulz")
object Rot13Interceptor : IInterceptor {
    fun String.rot13(): String = map {
        when (it) {
            in 'A'..'A' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

    private fun transform(message: Message): Message = message.copy(
        header = message.header.rot13(),
        body = message.body.rot13()
    )

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> =
        next(call.withMessage(transform(call.message))).map(::transform)
}


@Deprecated("Only use for the lulz")
object Encryption : IInterceptor {
    fun String.encrypt(): String = map { it + 3 }.joinToString("")
    fun String.decrypt(): String = map { it - 3 }.joinToString("")


    private fun encrypt(message: Message): Message =
        Message(header = message.header.encrypt(), body = message.body.encrypt())

    private fun decrypt(message: Message): Message =
        Message(header = message.header.decrypt(), body = message.body.decrypt())

    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> {
        val request = when (call) {
            is ClientCall -> encrypt(call.message)
            is ServerCall -> decrypt(call.message)
        }
        return next(call.withMessage(request)).map { response ->
            when (call) {
                is ClientCall -> decrypt(response)
                is ServerCall -> encrypt(response)
            }
        }
    }
}
