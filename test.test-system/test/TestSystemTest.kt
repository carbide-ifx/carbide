import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import ifx.host.Host
import ifx.host.ProtocolListener
import ifx.host.tooling.ServiceExplorer
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.protocol.rsocket.default
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.proxy.factory.jsonrpc.JsonRpcProxyFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds

class TestSystemTest {
    @Test
    fun `default host binds rsocket to the requested port`() {
        val port = ServerSocket(0).use { it.localPort }
        val host = Host.default(port).open()
        try {
            assertEquals(port, host.port(RSOCKET_PROTOCOL_ID))
        } finally {
            host.close()
        }
    }

    @Test
    fun `default host resolves port zero to an available port`() {
        val host = Host.default().open()
        try {
            assertNotEquals(0, host.port(RSOCKET_PROTOCOL_ID))
        } finally {
            host.close()
        }
    }

    @Test
    fun `service explorer requires an rsocket listener`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ServiceExplorer(ProtocolListener(JsonRpcServerProtocol()))
        }

        assertEquals("ServiceExplorer requires an RSocket listener", exception.message)
    }

    @Test
    fun `host can be created directly from one protocol`() {
        val host = Host(RSocketServerProtocol()).open()
        try {
            assertNotEquals(0, host.port(RSOCKET_PROTOCOL_ID))
        } finally {
            host.close()
        }
    }

    @Test
    fun `system serves products with prices over rsocket`() = runBlocking {
        val system = startTestSystem()
        try {
            val proxyFactory = RSocketProxyFactory.forHost(system)
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
    fun `system serves request response calls over json rpc on a separate port`() = runBlocking {
        val system = startTestSystem()
        try {
            val productAccess = JsonRpcProxyFactory.forHost(system).create<IProductAccess>()
            val products = productAccess.filter(ProductCriteria.id("bike-1"))

            assertEquals(listOf("bike-1"), products.map { it.id })
            assertNotEquals(
                system.port(RSOCKET_PROTOCOL_ID),
                system.port(JSON_RPC_PROTOCOL_ID),
            )
            assertFailsWith<ProtocolException> {
                productAccess.generateRandowProduct().first()
            }
            Unit
        } finally {
            system.close()
        }
    }

    @Test
    fun `host publishes its generated test UI catalog`() = runBlocking {
        val system = startTestSystem(emptyList())
        val client = HttpClient()
        try {
            val port = system.port(RSOCKET_PROTOCOL_ID)
            val catalogJson: String = client.get("http://localhost:$port/ifx/services").body()
            val catalog = RpcFormat.decodeFromString<ServiceCatalog>(catalogJson)

            assertEquals(
                HttpStatusCode.NotFound,
                client.get("http://localhost:${system.port(JSON_RPC_PROTOCOL_ID)}/").status,
            )

            assertEquals("Test System", catalog.name)
            assertEquals(
                listOf(RSOCKET_PROTOCOL_ID, JSON_RPC_PROTOCOL_ID),
                catalog.listeners.map { it.protocolId },
            )
            assertEquals(
                listOf(system.port(RSOCKET_PROTOCOL_ID), system.port(JSON_RPC_PROTOCOL_ID)),
                catalog.listeners.map { it.port },
            )
            assertEquals(
                listOf("IProductAccess", "IPricingEngine", "ISalesManager", "IActuator"),
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
