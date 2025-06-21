package ifx.protocol.contract.filters

import ifx.protocol.contract.IFilter
import ifx.protocol.contract.Message

class LoggingFilter(val prefix: String) : IFilter {
    override fun invoke(message: Message): Message = message.also { println("$prefix: $message") }
}

@Deprecated("Only use for the lulz")
class Rot13Filter : IFilter {
    fun String.rot13(): String = map {
        when (it) {
            in 'A'..'A' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

    override fun invoke(message: Message): Message = message.copy(
        header = message.header.rot13(),
        body = message.body.rot13()
    )
}
