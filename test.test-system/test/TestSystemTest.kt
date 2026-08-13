import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import engine.pricing.contract.IPricingEngine
import engine.pricing.contract.IPricingEngineDescriptor
import engine.pricing.service.PricingEngine
import ifx.actuator.LogTail
import ifx.actuator.LogTailSeverity
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
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.proxy.factory.jsonrpc.JsonRpcProxyFactory
import ifx.subsystem.default
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.rsocket.kotlin.RSocketError
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
        val proxyFactory = RSocketProxyFactory.forHost(system)
        try {
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
            proxyFactory.close()
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
                val protocol = RSocketClientProtocol(
                    baseUrl = { "ws://localhost:${proxy.port}" },
                    keepAlive = KeepAlive(interval = 100.milliseconds, maxLifetime = 300.milliseconds),
                )
                val productAccess = IProductAccessDescriptor.createClient(
                    protocol.createClientBinding(IProductAccessDescriptor.address),
                )
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
                    protocol.close()
                }
            }
        } finally {
            host.close()
        }
    }

    @Test
    fun `a dropped downstream connection is reported as a protocol failure and logged by the host`() =
        runBlocking {
            val hostPort = ServerSocket(0).use { it.localPort }
            val host = Host { listen(RSocketServerProtocol(), hostPort) }

            TcpProxy(hostPort).use { proxy ->
                // The engine reaches its own host through the proxy, so only its downstream
                // connection is dropped; the test client stays connected directly. A short
                // keep-alive is what bounds detection, since calls carry no timeout of their own.
                val engineFactory = RSocketProxyFactory(
                    port = proxy.port,
                    keepAlive = KeepAlive(interval = 100.milliseconds, maxLifetime = 300.milliseconds),
                )
                host.onClose { engineFactory.close() }
                host.registerService(
                    IProductAccessDescriptor,
                    ProductAccessEmulator().apply { seedTestData() },
                ).registerService(IPricingEngineDescriptor, PricingEngine(engineFactory))
                    .open()

                val clientFactory = RSocketProxyFactory(port = hostPort)
                LogTail.install()
                try {
                    val pricingEngine = clientFactory.create<IPricingEngine>()
                    assertEquals(3_500, pricingEngine.calculatePriceNok("bike-1"))

                    proxy.dropConnections()
                    delay(100.milliseconds)

                    // Previously a drop surfaced as a CancellationException: the caller looked
                    // cancelled rather than failed, and the host reported nothing.
                    val failure = assertFailsWith<ProtocolException> {
                        pricingEngine.calculatePriceNok("bike-1")
                    }
                    assertContains(failure.message.orEmpty(), IProductAccessDescriptor.address)

                    val entry = assertNotNull(
                        LogTail.logs(IPricingEngineDescriptor.address)
                            .lastOrNull { it.throwable?.contains("RSocket call") == true },
                        "the host should log the engine's unhandled downstream failure",
                    )
                    assertEquals(LogTailSeverity.Error, entry.severity)
                } finally {
                    clientFactory.close()
                    host.close()
                }
            }
        }

    @Test
    fun `a silently partitioned connection is replaced once the keep-alive expires`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.open()
        try {
            TcpProxy(hostPort).use { proxy ->
                val protocol = RSocketClientProtocol(
                    baseUrl = { "ws://localhost:${proxy.port}" },
                    keepAlive = KeepAlive(interval = 100.milliseconds, maxLifetime = 300.milliseconds),
                )
                val productAccess = IProductAccessDescriptor.createClient(
                    protocol.createClientBinding(IProductAccessDescriptor.address),
                )
                try {
                    assertEquals(emptyList(), productAccess.filter(ProductCriteria()))

                    // Neither peer sees a socket error, so only the keep-alive can notice. Calls are
                    // no longer bounded by a client-side timeout, making this the sole detection path.
                    proxy.blackholeConnections()

                    withTimeout(20.seconds) {
                        while (true) {
                            try {
                                assertEquals(emptyList(), productAccess.filter(ProductCriteria()))
                                break
                            } catch (_: Throwable) {
                                currentCoroutineContext().ensureActive()
                                delay(50.milliseconds)
                            }
                        }
                    }

                    assertTrue(proxy.acceptedConnectionCount >= 2)
                } finally {
                    protocol.close()
                }
            }
        } finally {
            host.close()
        }
    }

    @Test
    fun `repeatedly created proxies for one service share a single connection`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.open()
        try {
            TcpProxy(hostPort).use { proxy ->
                val proxyFactory = RSocketProxyFactory(port = proxy.port)
                try {
                    // The shape a manager uses: `val productAccess get() = proxyFactory.create()`.
                    repeat(5) {
                        assertEquals(emptyList(), proxyFactory.create<IProductAccess>().filter(ProductCriteria()))
                    }

                    assertEquals(1, proxy.acceptedConnectionCount)
                } finally {
                    proxyFactory.close()
                }
            }
        } finally {
            host.close()
        }
    }

    @Test
    fun `a failing remote operation keeps the shared connection`() = runBlocking {
        withProxiedHost(ThrowingProductAccess("business rule violated")) { proxy, proxyFactory ->
            val productAccess = proxyFactory.create<IProductAccess>()
            assertEquals(emptyList(), productAccess.filter(ProductCriteria()))

            // An error frame is per-stream, so it must not fail the callers sharing the connection.
            val failure = assertFailsWith<ProtocolException> {
                productAccess.store(access.product.contract.Product.Bike("bike-1", 3))
            }
            assertContains(failure.message.orEmpty(), "business rule violated")
            assertIs<RSocketError.ApplicationError>(failure.cause)

            assertEquals(emptyList(), productAccess.filter(ProductCriteria()))
            assertEquals(1, proxy.acceptedConnectionCount)
        }
    }

    @Test
    fun `abandoning a stream early keeps the shared connection`() = runBlocking {
        withProxiedHost(ProductAccessEmulator()) { proxy, proxyFactory ->
            val productAccess = proxyFactory.create<IProductAccess>()
            assertEquals(emptyList(), productAccess.filter(ProductCriteria()))

            // `first()` aborts the flow while the service is still emitting.
            withTimeout(10.seconds) { productAccess.generateRandowProduct().first() }

            assertEquals(emptyList(), productAccess.filter(ProductCriteria()))
            assertEquals(1, proxy.acceptedConnectionCount)
        }
    }

    private suspend fun withProxiedHost(
        service: IProductAccess,
        block: suspend (TcpProxy, RSocketProxyFactory) -> Unit,
    ) {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, service)

        host.open()
        try {
            TcpProxy(hostPort).use { proxy ->
                val proxyFactory = RSocketProxyFactory(port = proxy.port)
                try {
                    block(proxy, proxyFactory)
                } finally {
                    proxyFactory.close()
                }
            }
        } finally {
            host.close()
        }
    }

    private class ThrowingProductAccess(
        private val failureMessage: String,
    ) : IProductAccess by ProductAccessEmulator() {
        override suspend fun store(product: access.product.contract.Product): Unit = error(failureMessage)
    }

    @Test
    fun `system serves request response calls over json rpc on a separate port`() = runBlocking {
        val system = startTestSystem()
        val proxyFactory = JsonRpcProxyFactory.forHost(system)
        try {
            val productAccess = proxyFactory.create<IProductAccess>()
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
            proxyFactory.close()
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

    /**
     * Keeps the existing sockets open but stops delivering bytes in either direction, so neither
     * peer observes an error. Connections opened afterwards are forwarded normally.
     */
    fun blackholeConnections() {
        synchronized(connectionsLock) { connections.toList() }.forEach(Connection::blackhole)
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
        private val blackholed = AtomicBoolean()

        fun start() {
            forward(client, target)
            forward(target, client)
        }

        fun blackhole() {
            blackholed.set(true)
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
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.getInputStream().read(buffer)
                        if (read < 0) break
                        if (blackholed.get()) continue // read and discard, leaving both peers unaware

                        destination.getOutputStream().write(buffer, 0, read)
                        destination.getOutputStream().flush()
                    }
                } catch (_: Throwable) {
                    // Closing either side is the mechanism used to simulate the dropped connection.
                } finally {
                    close()
                }
            }
        }
    }
}
