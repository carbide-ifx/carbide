import access.product.contract.IProductAccessDescriptor
import access.product.contract.Product
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.decode
import ifx.protocol.contract.encodeToMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RpcInteractionTest {
    @Test
    fun `Unit defaults to request-response and annotation selects fire-and-forget`() = runBlocking {
        val interactions = mutableListOf<String>()
        val client = IProductAccessDescriptor.createClient(object : IBinding {
            override suspend fun fireAndForget(operation: String, message: Message) {
                interactions += "fire-and-forget:$operation"
            }

            override suspend fun requestResponse(operation: String, message: Message): Message {
                interactions += "request-response:$operation"
                return Unit.encodeToMessage()
            }

            override suspend fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
        })

        client.store(ProductTestData.car)
        client.notifyProductViewed(ProductTestData.car.id)

        assertEquals(
            listOf(
                "request-response:store(access.product.contract.Product)",
                "fire-and-forget:notifyProductViewed(kotlin.String)",
            ),
            interactions,
        )
    }

    @Test
    fun `Unit request-response acknowledgement is valid protocol JSON`() = runBlocking {
        val service = access.product.service.ProductAccessEmulator()
        val response = IProductAccessDescriptor.bind(service).requestResponse(
            "store(access.product.contract.Product)",
            (ProductTestData.car as Product).encodeToMessage(),
        )

        assertEquals(Unit, response.decode<Unit>())
        assertEquals(ProductTestData.car, service.db[ProductTestData.car.id])
    }
}
