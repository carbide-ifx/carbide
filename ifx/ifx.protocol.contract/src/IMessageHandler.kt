package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow

/**
 * Binds a service to a protocol endpoint.
 */
interface IMessageHandler {
    suspend fun fireAndForget(operation: String, message: Message): Unit
    suspend fun requestResponse(operation: String, message: Message): Message
    suspend fun requestStream(operation: String, message: Message): Flow<Message>
}
