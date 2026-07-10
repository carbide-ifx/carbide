package ifx.protocol.contract.interceptors

import ifx.logging.Log
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.Message

class LoggingInterceptor(val prefix: String) : IInterceptor {
    val log = Log {}
    override suspend fun onClientSend(operation: String, message: Message): Message = message.also { log.info { "Client send: $prefix $operation: $message" } }
    override suspend fun onServerReceive(operation: String, message: Message): Message = message.also { log.info { "Server receive: $prefix $operation: $message" } }
    override suspend fun onServerSend(operation: String, message: Message): Message = message.also { log.info { "Server send: $prefix $operation: $message" } }
    override suspend fun onClientReceive(operation: String, message: Message): Message = message.also { log.info { "Client receive: $prefix $operation: $message" } }
}
