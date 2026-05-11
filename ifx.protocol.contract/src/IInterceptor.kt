package ifx.protocol.contract

interface IInterceptor {
    suspend fun onClientSend(operation: String, message: Message): Message = message
    suspend fun onServerReceive(operation: String, message: Message): Message = message
    suspend fun onServerSend(operation: String, message: Message): Message = message
    suspend fun onClientReceive(operation: String, message: Message): Message = message
}

