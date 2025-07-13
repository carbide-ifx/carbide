package ifx.protocol.contract.filters

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.Message

@Deprecated("Only use for the lulz")
class Rot13Interceptor : IInterceptor {
    fun String.rot13(): String = map {
        when (it) {
            in 'A'..'A' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

    override suspend fun invoke(operation: String, message: Message): Message = message.copy(
        header = message.header.rot13(),
        body = message.body.rot13()
    )
}
