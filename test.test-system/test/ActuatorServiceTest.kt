import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import ifx.actuator.IActuator
import ifx.actuator.LogTail
import ifx.actuator.logTail
import ifx.host.HostHealth
import ifx.host.HostState
import ifx.host.ServiceHealthSnapshot
import ifx.protocol.contract.ServiceKind
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.proxy.factory.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.telemetry.otel.OpenTelemetryRpcInterceptor
import ifx.telemetry.otel.trace.SpanExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ActuatorServiceTest {
    @Test
    fun `actuator exposes the live host catalog through IFX`() = runBlocking {
        val system = startTestSystem(emptyList())
        val proxyFactory = RSocketProxyFactory.forHost(system)
        try {
            val catalog = proxyFactory
                .create<IActuator>()
                .catalog()

            assertEquals("Test System", catalog.name)
            assertEquals(
                listOf("IActuator", "IProductAccess", "IPricingEngine", "ISalesManager"),
                catalog.services.map { it.name },
            )
            assertEquals(
                ServiceKind.SERVICE,
                catalog.services.single { it.name == "IProductAccess" }.kind,
            )
            assertEquals(ServiceKind.UTILITY, catalog.services.single { it.name == "IActuator" }.kind)
            assertEquals(
                listOf(RSOCKET_PROTOCOL_ID, JSON_RPC_PROTOCOL_ID),
                catalog.listeners.map { it.protocolId },
            )
            catalog.listeners.forEach { listener -> assertNotEquals(0, listener.port) }
        } finally {
            proxyFactory.close()
            system.stop()
        }
    }

    @Test
    fun `service contracts do not expose host lifecycle operations`() = runBlocking {
        val system = startTestSystem(emptyList())
        val proxyFactory = RSocketProxyFactory.forHost(system)
        try {
            val exposedLifecycleOperations = proxyFactory
                .create<IActuator>()
                .catalog()
                .services
                .flatMap { service ->
                    service.operations
                        .filter { it.name in setOf("init", "status", "isReady", "isLive", "start", "stop") }
                        .map { "${service.name}.${it.name}" }
                }

            assertEquals(emptyList(), exposedLifecycleOperations)
        } finally {
            proxyFactory.close()
            system.stop()
        }
    }

    @Test
    fun `actuator exposes aggregate host health through IFX`() = runBlocking {
        val system = startTestSystem(emptyList())
        val proxyFactory = RSocketProxyFactory.forHost(system)
        try {
            assertEquals(
                HostHealth(
                    state = HostState.READY,
                    ready = true,
                    live = true,
                    services = listOf(
                        ServiceHealthSnapshot("ifx.actuator.IActuator", ready = true, live = true),
                        ServiceHealthSnapshot("access.product.contract.IProductAccess", ready = true, live = true),
                        ServiceHealthSnapshot("engine.pricing.contract.IPricingEngine", ready = true, live = true),
                        ServiceHealthSnapshot("manager.sales.contract.ISalesManager", ready = true, live = true),
                    ),
                ),
                proxyFactory.create<IActuator>().health(),
            )
        } finally {
            proxyFactory.close()
            system.stop()
        }
    }

    @Test
    fun `actuator streams retained service log entries through IFX`() = runBlocking {
        val system = startTestSystem(listOf(rpcLoggingInterceptor()))
        val proxyFactory = RSocketProxyFactory.forHost(system)
        try {
            val products = proxyFactory.create<IProductAccess>().filter(ProductCriteria())
            val message = "Found ${products.size} products"

            val entry = withTimeout(10.seconds) {
                proxyFactory
                    .create<IActuator>()
                    .logTail<IProductAccess>()
                    .first { it.message == message }
            }

            assertEquals("access.product.contract.IProductAccess", entry.serviceInterface)
            assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
            assertEquals(emptyList(), entry.path)
            assertEquals(message, entry.message)
        } finally {
            proxyFactory.close()
            system.stop()
        }
    }

    @Test
    fun `actuator log tail does not retain its own RPC traffic`() = runBlocking {
        val system = startTestSystem(listOf(rpcLoggingInterceptor()))
        val proxyFactory = RSocketProxyFactory.forHost(system)
        val actuatorInterface = "ifx.actuator.IActuator"
        try {
            val previousSequence = LogTail.logs(actuatorInterface).lastOrNull()?.sequence ?: 0L

            val selfGeneratedEntry = withTimeoutOrNull(500.milliseconds) {
                proxyFactory
                    .create<IActuator>()
                    .logTail(actuatorInterface)
                    .first { it.sequence > previousSequence }
            }

            assertNull(selfGeneratedEntry)
        } finally {
            proxyFactory.close()
            system.stop()
        }
    }

    private fun rpcLoggingInterceptor() = OpenTelemetryRpcInterceptor(
        exporter = SpanExporter {},
        serviceName = "test-system",
        logRpcCalls = true,
    )
}
