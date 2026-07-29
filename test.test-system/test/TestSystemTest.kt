import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
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

    }
}
