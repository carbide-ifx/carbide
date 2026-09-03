import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import engine.pricing.contract.IPricingEngine
import engine.pricing.contract.IPricingEngineDescriptor
import engine.pricing.service.PricingEngine
import ifx.actuator.LogTail
import ifx.actuator.LogTailSeverity
import ifx.host.Host
import ifx.host.HostHealth
import ifx.host.HostState
import ifx.host.ProtocolListener
import ifx.logging.installLogWriter
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.interceptors.ContextInterceptor
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.EXTERNAL_KEEP_ALIVE
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.proxy.factory.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.proxy.factory.jsonrpc.JsonRpcProxyFactory
import ifx.service.explorer.ServiceExplorer
import ifx.subsystem.default
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
import kotlinx.serialization.json.Json
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
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
    fun `test system logs its service explorer address on startup`() = runBlocking {
        val logWriter = HostLogWriter()
        installLogWriter(logWriter)
        val system = startTestSystem(emptyList())
        try {
            assertContains(
                logWriter.messages,
                "Service Explorer: http://localhost:${system.port(RSOCKET_PROTOCOL_ID)}/",
            )
        } finally {
            system.stop()
        }
    }

    @Test
    fun `default host publishes Kubernetes health endpoints`() = runBlocking {
        val host = Host.default().start()
        val client = HttpClient()
        try {
            val baseUrl = "http://localhost:${host.port(RSOCKET_PROTOCOL_ID)}/ifx/health"
            val ready = client.get("$baseUrl/ready")
            val live = client.get("$baseUrl/live")
            val detail = client.get(baseUrl)

            assertEquals(
                listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.OK),
                listOf(ready.status, live.status, detail.status),
            )
            assertEquals(HostState.READY, Json.decodeFromString<HostHealth>(detail.bodyAsText()).state)
        } finally {
            client.close()
            host.stop()
        }
    }

    @Test
    fun `default host binds both standard protocols and bundled service explorer`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val host = Host.default(rsocketPort = port).start()
        val client = HttpClient()
        try {
            assertEquals(port, host.port(RSOCKET_PROTOCOL_ID))
            assertNotEquals(0, host.port(JSON_RPC_PROTOCOL_ID))
            assertIs<ContextInterceptor>(host.interceptors.single())
            assertEquals(listOf("IActuator"), host.serviceCatalog().services.map { it.name })
            assertEquals(HttpStatusCode.OK, client.get("http://localhost:$port/").status)
        } finally {
            client.close()
            host.stop()
        }
    }

    @Test
    fun `default host resolves port zero to an available port`() = runBlocking {
        val host = Host.default().start()
        try {
            assertNotEquals(0, host.port(RSOCKET_PROTOCOL_ID))
            assertNotEquals(0, host.port(JSON_RPC_PROTOCOL_ID))
            assertNotEquals(host.port(RSOCKET_PROTOCOL_ID), host.port(JSON_RPC_PROTOCOL_ID))
        } finally {
            host.stop()
        }
    }

    @Test
    fun `host keeps context mandatory when additional interceptors are supplied`() {
        val additional = IInterceptor { call, next -> next(call) }
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
            Host {
                listen(JsonRpcServerProtocol()) { install(ServiceExplorer()) }
            }
        }

        assertEquals(
            "ServiceExplorer requires a rsocket listener but was installed on json-rpc",
            exception.message,
        )
    }

    @Test
    fun `host can be created directly from one protocol`() = runBlocking {
        val host = Host {
            listen(RSocketServerProtocol())
        }.start()
        try {
            assertNotEquals(0, host.port(RSOCKET_PROTOCOL_ID))
        } finally {
            host.stop()
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
            system.stop()
        }
    }

    @Test
    fun `rsocket client reconnects after the transport connection drops`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.start()
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
            host.stop()
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
                host.onStop { engineFactory.close() }
                host.registerService(
                    IProductAccessDescriptor,
                    ProductAccessEmulator().apply { seedTestData() },
                ).registerService(IPricingEngineDescriptor, PricingEngine(engineFactory))
                    .start()

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
                    host.stop()
                }
            }
        }

    @Test
    fun `a silently partitioned connection is replaced once the keep-alive expires`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.start()
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
            host.stop()
        }
    }

    @Test
    fun `clients on one listener keep independent keep-alive windows`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.start()
        try {
            // A separate proxy per client so their connection counts can be told apart.
            TcpProxy(hostPort).use { backendRoute ->
                TcpProxy(hostPort).use { externalRoute ->
                    val backend = RSocketProxyFactory(
                        port = backendRoute.port,
                        keepAlive = KeepAlive(interval = 100.milliseconds, maxLifetime = 300.milliseconds),
                    )
                    val external = RSocketProxyFactory(
                        port = externalRoute.port,
                        keepAlive = EXTERNAL_KEEP_ALIVE,
                    )
                    try {
                        val backendAccess = backend.create<IProductAccess>()
                        assertEquals(emptyList(), backendAccess.filter(ProductCriteria()))
                        assertEquals(emptyList(), external.create<IProductAccess>().filter(ProductCriteria()))
                        assertEquals(1, backendRoute.acceptedConnectionCount)
                        assertEquals(1, externalRoute.acceptedConnectionCount)

                        backendRoute.blackholeConnections()
                        externalRoute.blackholeConnections()

                        withTimeout(20.seconds) {
                            while (true) {
                                try {
                                    backendAccess.filter(ProductCriteria())
                                    break
                                } catch (_: Throwable) {
                                    currentCoroutineContext().ensureActive()
                                    delay(50.milliseconds)
                                }
                            }
                        }

                        // The server honours whatever each client proposed, so the tight window
                        // reconnects while the generous one is still treating its peer as alive.
                        assertEquals(2, backendRoute.acceptedConnectionCount)
                        assertEquals(1, externalRoute.acceptedConnectionCount)
                    } finally {
                        backend.close()
                        external.close()
                    }
                }
            }
        } finally {
            host.stop()
        }
    }

    @Test
    fun `repeatedly created proxies for one service share a single connection`() = runBlocking {
        val hostPort = ServerSocket(0).use { it.localPort }
        val host = Host {
            listen(RSocketServerProtocol(), hostPort)
        }.registerService(IProductAccessDescriptor, ProductAccessEmulator())

        host.start()
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
            host.stop()
        }
    }

    @Test
    fun `destination-bound rsocket proxies route to their explicit hosts and reuse connections`() = runBlocking {
        val firstHost = Host {
            listen(RSocketServerProtocol())
        }.registerService(
            IProductAccessDescriptor,
            ProductAccessEmulator(mutableMapOf("first" to access.product.contract.Product.Bike("first", 3))),
        )
        val secondHost = Host {
            listen(RSocketServerProtocol())
        }.registerService(
            IProductAccessDescriptor,
            ProductAccessEmulator(mutableMapOf("second" to access.product.contract.Product.Bike("second", 7))),
        )

        firstHost.start()
        secondHost.start()
        try {
            TcpProxy(firstHost.port(RSOCKET_PROTOCOL_ID)).use { firstProxy ->
                TcpProxy(secondHost.port(RSOCKET_PROTOCOL_ID)).use { secondProxy ->
                    val proxyFactory = RSocketProxyFactory(port = firstProxy.port)
                    try {
                        val firstEndpoint = ServiceEndpoint("localhost", firstProxy.port)
                        val secondEndpoint = ServiceEndpoint("localhost", secondProxy.port)

                        repeat(2) {
                            assertEquals(
                                listOf("first"),
                                proxyFactory.at(firstEndpoint)
                                    .create<IProductAccess>()
                                    .filter(ProductCriteria())
                                    .map { it.id },
                            )
                            assertEquals(
                                listOf("second"),
                                proxyFactory.at(secondEndpoint)
                                    .create<IProductAccess>()
                                    .filter(ProductCriteria())
                                    .map { it.id },
                            )
                        }

                        assertEquals(1, firstProxy.acceptedConnectionCount)
                        assertEquals(1, secondProxy.acceptedConnectionCount)
                    } finally {
                        proxyFactory.close()
                    }
                }
            }
        } finally {
            secondHost.stop()
            firstHost.stop()
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

        host.start()
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
            host.stop()
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
            system.stop()
        }
    }

    @Test
    fun `destination-bound json rpc proxy routes to its explicit host`() = runBlocking {
        val remote = Host {
            listen(JsonRpcServerProtocol())
        }.registerService(
            IProductAccessDescriptor,
            ProductAccessEmulator(mutableMapOf("remote" to access.product.contract.Product.Bike("remote", 5))),
        ).start()
        val proxyFactory = JsonRpcProxyFactory(port = 1)

        try {
            val productAccess = proxyFactory
                .at(ServiceEndpoint("localhost", remote.port(JSON_RPC_PROTOCOL_ID)))
                .create<IProductAccess>()

            assertEquals(
                listOf("remote"),
                productAccess.filter(ProductCriteria()).map { it.id },
            )
        } finally {
            proxyFactory.close()
            remote.stop()
        }
    }

    @Test
    fun `host serves the bundled explorer webapp without a separate catalog endpoint`() =
        runBlocking {
            val system = startTestSystem(emptyList())
            val client = HttpClient()
            try {
                val port = system.port(RSOCKET_PROTOCOL_ID)
                val html: String = client.get("http://localhost:$port/").body()
                val javascript = client.get("http://localhost:$port/test-ui.js")

                assertEquals(true, "iFX Service Explorer" in html)
                assertEquals(HttpStatusCode.OK, javascript.status)
                assertEquals("gzip", javascript.headers[HttpHeaders.ContentEncoding])
                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("http://localhost:$port/ifx/services").status,
                )
                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("http://localhost:${system.port(JSON_RPC_PROTOCOL_ID)}/").status,
                )
            } finally {
                client.close()
                system.stop()
            }
        }
}

private class HostLogWriter : LogWriter() {
    val messages = CopyOnWriteArrayList<String>()

    override fun isLoggable(tag: String, severity: Severity): Boolean = tag == "Host"

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        messages += message
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
