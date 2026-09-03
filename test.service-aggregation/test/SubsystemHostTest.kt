package test.service.aggregation

import ifx.host.Host
import ifx.context.Context
import ifx.logging.Log
import ifx.protocol.contract.Message
import ifx.protocol.contract.headers
import ifx.service.IService
import ifx.stdlib.TimeSpan
import ifx.subsystem.development
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsystemHostTest {
    @Test
    fun `subsystem exports its public service programming surface`() {
        val headers: Map<String, JsonElement> = Message("{}", "").headers()
        val context: Context = Context.Empty
        val log: Log = ExportedService().log
        val timeSpan: TimeSpan? = null

        assertTrue(headers.isEmpty())
        assertTrue(context.isEmpty)
        assertTrue(log.tag.isNotBlank())
        assertEquals(null, timeSpan)
    }

    @Test
    fun `development host includes the standard subsystem tooling`() = runBlocking {
        val host = Host.development()

        assertTrue(dependencyServiceDescriptor.address.isNotBlank())
        assertEquals("Service Host", host.name)
        assertEquals(listOf("IActuator"), host.serviceCatalog().services.map { it.name })
    }

    @Test
    fun `generated descriptor exposes typed operation metadata`() {
        assertEquals("filter(access.product.contract.ProductCriteria)", dependencyFilterOperation.description.route)
        assertEquals("generateRandowProduct()", dependencyStreamOperation.description.route)
        assertEquals("notifyProductViewed(kotlin.String)", dependencyFireAndForgetOperation.description.route)
    }
}

private class ExportedService : IService
