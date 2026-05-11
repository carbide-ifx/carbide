package ifx.protocol.contract.interceptors

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.Message

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

    override suspend fun onClientSend(operation: String, message: Message): Message = transform(message)
    override suspend fun onServerReceive(operation: String, message: Message): Message = transform(message)
    override suspend fun onServerSend(operation: String, message: Message): Message = transform(message)
    override suspend fun onClientReceive(operation: String, message: Message): Message = transform(message)
}


@Deprecated("Only use for the lulz")
object Encryption : IInterceptor {
    fun String.encrypt(): String = map { it + 3 }.joinToString("")
    fun String.decrypt(): String = map { it - 3 }.joinToString("")


    override suspend fun onClientSend(operation: String, message: Message): Message = Message(header = message.header.encrypt(), body = message.body.encrypt())
    override suspend fun onServerReceive(operation: String, message: Message): Message = Message(header = message.header.decrypt(), body = message.body.decrypt())
    override suspend fun onServerSend(operation: String, message: Message): Message = Message(header = message.header.encrypt(), body = message.body.encrypt())
    override suspend fun onClientReceive(operation: String, message: Message): Message = Message(header = message.header.decrypt(), body = message.body.decrypt())
}

