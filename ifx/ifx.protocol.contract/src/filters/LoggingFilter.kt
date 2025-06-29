package ifx.protocol.contract.filters

import ifx.protocol.contract.IFilter
import ifx.protocol.contract.Message

class LoggingFilter(val prefix: String) : IFilter {
    override suspend fun invoke(operation: String, message: Message): Message = message.also { println("$prefix $operation: $message") }
}

