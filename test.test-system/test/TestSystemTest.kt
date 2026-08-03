import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TestSystemTest {
    @Test
    fun `system serves products with prices over rsocket`() = runBlocking {
        val system = startTestSystem()
        try {
            val proxyFactory = ProxyFactory.forHost(system)
            withTimeout(10.seconds) {
                val products = proxyFactory.create<ISalesManager>()
                    .listProducts()
                    .toList()
                    .sortedBy(Product::id)

                assertEquals(
                    listOf(
                        Product("bike-1", "A Bike with 12 gears", 3_500),
                        Product("car-1", "A blue car from Volvo", 250_000),
                    ),
                    products,
                )
            }
        } finally {
            system.close()
        }
    }

    @Test
    fun `host publishes its generated test UI catalog`() = runBlocking {
        val system = startTestSystem(emptyList())
        val client = HttpClient()
        try {
            val port = (system.protocol as RSocketProtocol).port
            val catalogJson: String = client.get("http://localhost:$port/ifx/services").body()
            val catalog = RpcFormat.decodeFromString<ServiceCatalog>(catalogJson)

            assertEquals("Test System", catalog.name)
            assertEquals(
                listOf("IProductAccess", "IPricingEngine", "ISalesManager"),
                catalog.services.map { it.name },
            )
            assertEquals(
                listOf("filter", "generateRandowProduct", "store", "notifyProductViewed", "status", "init", "isReady", "isLive"),
                catalog.services.first().operations.map { it.name },
            )
        } finally {
            client.close()
            system.close()
        }
    }
}
