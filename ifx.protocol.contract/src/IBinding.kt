package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow

/**
 * Binds a service to a protocol endpoint.
 */
interface IBinding {
    suspend fun fireAndForget(operation: String, message: Message): Unit
    suspend fun requestResponse(operation: String, message: Message): Message
    fun requestStream(operation: String, message: Message): Flow<Message>
}
