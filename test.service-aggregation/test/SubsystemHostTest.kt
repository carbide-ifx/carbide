package test.service.aggregation

import ifx.host.Host
import ifx.subsystem.default
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsystemHostTest {
    @Test
    fun `stable default host needs no generated subsystem registry`() {
        val host = Host.default()

        assertTrue(dependencyServiceDescriptor.address.isNotBlank())
        assertEquals("Service Host", host.name)
    }
}
