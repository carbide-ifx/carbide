import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import ifx.actuator.IActuator
import ifx.actuator.logTail
import ifx.logging.Log
import ifx.protocol.contract.forService
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ActuatorServiceTest {
    @Test
    fun `actuator streams retained and future log-tail entries through IFX`() = runBlocking {
        val system = startTestSystem(emptyList())
        val message = "streamed log-tail entry"
        try {
            Log.forService<IProductAccess>(ProductAccessEmulator())
                .withTag("Repository")
                .info { message }

            val entry = withTimeout(10.seconds) {
                RSocketProxyFactory.forHost(system)
                    .create<IActuator>()
                    .logTail<IProductAccess>()
                    .first { it.message == message }
            }

            assertEquals("access.product.contract.IProductAccess", entry.serviceInterface)
            assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
            assertEquals(listOf("Repository"), entry.path)
            assertEquals(message, entry.message)
        } finally {
            system.close()
        }
    }
}
