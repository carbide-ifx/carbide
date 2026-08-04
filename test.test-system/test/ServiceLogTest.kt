import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.service.ProductAccessEmulator
import ifx.actuator.ActuatorLogs
import ifx.logging.Log
import ifx.protocol.contract.forService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServiceLogTest {
    @Test
    fun `service logger derives its identity from the descriptor and implementation`() {
        val service = ProductAccessEmulator()
        val log = Log.forService<IProductAccess>(service).withTag("Repository")
        val message = "service-scoped actuator log"

        ActuatorLogs.install()
        log.info { message }

        val entry = assertNotNull(
            ActuatorLogs.logs(IProductAccessDescriptor.address).lastOrNull { it.message == message }
        )
        assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
        assertEquals(listOf("Repository"), entry.path)
    }
}
