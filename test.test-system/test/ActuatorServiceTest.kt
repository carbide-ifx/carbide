import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import ifx.actuator.IActuator
import ifx.actuator.logTail
import ifx.logging.Log
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.forService
import ifx.protocol.jsonrpc.JSON_RPC_PROTOCOL_ID
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
            system.close()
        }
    }

    @Test
    fun `actuator streams retained and future log-tail entries through IFX`() = runBlocking {
        val system = startTestSystem(emptyList())
        val proxyFactory = RSocketProxyFactory.forHost(system)
        val message = "streamed log-tail entry"
        try {
            Log.forService<IProductAccess>(ProductAccessEmulator())
                .withTag("Repository")
                .info { message }

            val entry = withTimeout(10.seconds) {
                proxyFactory
                    .create<IActuator>()
                    .logTail<IProductAccess>()
                    .first { it.message == message }
            }

            assertEquals("access.product.contract.IProductAccess", entry.serviceInterface)
            assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
            assertEquals(listOf("Repository"), entry.path)
            assertEquals(message, entry.message)
        } finally {
            proxyFactory.close()
            system.close()
        }
    }
}
