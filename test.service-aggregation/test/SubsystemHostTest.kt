package test.service.aggregation

import ifx.host.Host
import ifx.subsystem.default
import kotlin.test.Test
import kotlin.test.assertSame

class SubsystemHostTest {
    @Test
    fun `stable default host uses the generated subsystem registry`() {
        val host = Host.default()

        assertSame(dependencyServiceDescriptors, host.serviceDescriptors)
    }
}
