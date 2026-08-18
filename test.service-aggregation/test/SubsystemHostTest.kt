package test.service.aggregation

import ifx.host.Host
import ifx.subsystem.default
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsystemHostTest {
    @Test
    fun `default host includes the standard subsystem tooling`() = runBlocking {
        val host = Host.default()

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
