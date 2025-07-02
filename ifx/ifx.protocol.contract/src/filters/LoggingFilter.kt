package ifx.protocol.contract.filters

import ifx.logging.Log
import ifx.protocol.contract.IFilter
import ifx.protocol.contract.Message

class LoggingFilter(val prefix: String) : IFilter {
    val log = Log {}
    override suspend fun invoke(operation: String, message: Message): Message =
        message.also { log.info {"$prefix $operation: $message" } }
}

