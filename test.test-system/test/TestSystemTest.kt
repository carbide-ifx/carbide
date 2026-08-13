import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.host.ProtocolListener
import ifx.host.tooling.ServiceExplorer
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.interceptors.ContextInterceptor
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketClient
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.proxy.factory.jsonrpc.JsonRpcProxyFactory
import ifx.subsystem.default
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.rsocket.kotlin.keepalive.KeepAlive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TestSystemTest {
    @Test
    fun `default host binds both standard protocols and tooling`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val host = Host.default(rsocketPort = port).open()
        val client = HttpClient()
        try {
            assertEquals(port, host.port(RSOCKET_PROTOCOL_ID))
            assertNotEquals(0, host.port(JSON_RPC_PROTOCOL_ID))
            assertIs<ContextInterceptor>(host.interceptors.single())
            assertEquals(listOf("IActuator"), host.serviceCatalog().services.map { it.name })
            assertEquals(HttpStatusCode.OK, client.get("http://localhost:$port/").status)
        } finally {
            client.close()
            host.close()
        }
    }

    @Test
    fun `default host resolves port zero to an available port`() = runBlocking {
        val host = Host.default().open()
        try {
            assertNotEquals(0, host.port(RSOCKET_PROTOCOL_ID))
            assertNotEquals(0, host.port(JSON_RPC_PROTOCOL_ID))
            assertNotEquals(host.port(RSOCKET_PROTOCOL_ID), host.port(JSON_RPC_PROTOCOL_ID))
        } finally {
            host.close()
        }
    }

    @Test
    fun `host keeps context mandatory when additional interceptors are supplied`() {
        val additional = LoggingInterceptor()
        val host = Host {
            listen(RSocketServerProtocol())
        }.addInterceptors(additional)

        assertEquals(2, host.interceptors.size)
        assertIs<ContextInterceptor>(host.interceptors.first())
        assertSame(additional, host.interceptors.last())
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
        val host = Host {
            listen(RSocketServerProtocol())
        }.open()
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
    fun `rsocket client reconnects after the transport connection drops`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.open()
        try {
            TcpProxy(hostPort).use { proxy ->
                val binding = RSocketClient(
                    url = "ws://localhost:${proxy.port}/${IProductAccessDescriptor.address}",
                    keepAlive = KeepAlive(interval = 100.milliseconds, maxLifetime = 300.milliseconds),
                )
                val productAccess = IProductAccessDescriptor.createClient(binding)
                try {
                    assertEquals(emptyList(), productAccess.filter(ProductCriteria()))

                    proxy.dropConnections()
                    delay(100.milliseconds)

                    withTimeout(10.seconds) {
                        while (true) {
                            try {
                                val products = productAccess.filter(ProductCriteria())
                                assertEquals(emptyList(), products)
                                assertTrue(proxy.acceptedConnectionCount >= 2)
                                break
                            } catch (_: Throwable) {
                                // A call racing with disconnect detection is failed, never replayed.
                                currentCoroutineContext().ensureActive()
                                delay(50.milliseconds)
                            }
                        }
                    }
                } finally {
                    binding.httpClient.close()
                }
            }
        } finally {
            host.close()
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
    fun `host serves the explorer webapp without a separate catalog endpoint`() = runBlocking {
        val system = startTestSystem(emptyList())
        val client = HttpClient()
        try {
            val port = system.port(RSOCKET_PROTOCOL_ID)
            val html: String = client.get("http://localhost:$port/").body()

            assertEquals(true, "iFX Service Explorer" in html)
            assertEquals(HttpStatusCode.OK, client.get("http://localhost:$port/ifx/test-ui.js").status)
            assertEquals(HttpStatusCode.NotFound, client.get("http://localhost:$port/ifx/services").status)
            assertEquals(
                HttpStatusCode.NotFound,
                client.get("http://localhost:${system.port(JSON_RPC_PROTOCOL_ID)}/").status,
            )
        } finally {
            client.close()
            system.close()
        }
    }
}

private class TcpProxy(
    private val targetPort: Int,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    private val connections = mutableSetOf<Connection>()
    private val connectionsLock = Any()
    private val acceptedConnections = AtomicInteger()

    val port: Int = serverSocket.localPort
    val acceptedConnectionCount: Int get() = acceptedConnections.get()

    init {
        thread(name = "rsocket-test-proxy-accept", isDaemon = true) {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    val connection = Connection(client, Socket("localhost", targetPort))
                    acceptedConnections.incrementAndGet()
                    synchronized(connectionsLock) { connections += connection }
                    connection.start()
                } catch (exception: SocketException) {
                    if (!serverSocket.isClosed) throw exception
                }
            }
        }
    }

    fun dropConnections() {
        synchronized(connectionsLock) { connections.toList() }.forEach(Connection::close)
    }

    override fun close() {
        serverSocket.close()
        dropConnections()
    }

    private inner class Connection(
        private val client: Socket,
        private val target: Socket,
    ) {
        private val closed = AtomicBoolean()

        fun start() {
            forward(client, target)
            forward(target, client)
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            client.close()
            target.close()
            synchronized(connectionsLock) { connections -= this }
        }

        private fun forward(source: Socket, destination: Socket) {
            thread(name = "rsocket-test-proxy-forward", isDaemon = true) {
                try {
                    source.getInputStream().copyTo(destination.getOutputStream())
                } catch (_: Throwable) {
                    // Closing either side is the mechanism used to simulate the dropped connection.
                } finally {
                    close()
                }
            }
        }
    }
}
