package test

import access.product.contract.IProductAccessDescriptor
import access.product.service.ProductAccessEmulator
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import kotlinx.coroutines.flow.Flow
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceLoggerTest {
    @Test
    fun `service implementation logger uses its qualified class name as tag`() {
        assertEquals(
            "access.product.service.ProductAccessEmulator",
            ProductAccessEmulator().logger.tag,
        )
    }

    @Test
    fun `generated client uses a named proxy class and matching logger tag`() {
        val client = IProductAccessDescriptor.createClient(UnusedBinding)

        assertEquals(
            "access.product.contract.IProductAccessProxy",
            client::class.qualifiedName,
        )
        assertEquals(
            "access.product.contract.IProductAccessProxy",
            client.logger.tag,
        )
    }
}

private object UnusedBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message): Nothing = error("Not used")
    override suspend fun requestResponse(operation: String, message: Message): Nothing = error("Not used")
    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = error("Not used")
}
