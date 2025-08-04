package ifx.protocol.contract.filters

import ifx.logging.Log
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.Message


class LoggingInterceptor(val prefix: String) : IInterceptor {
    val log = Log {}
    override suspend fun invoke(operation: String, message: Message): Message {
        log.info { "$prefix $operation: $message" }
        return message
    }
}
