package ifx.host

import ifx.protocol.contract.ProtocolListenerDescription
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostStartupDiagramTest {
    @Test
    fun `renders hosted services in service explorer architecture layers`() {
        val catalog = ServiceCatalog(
            name = "Order System",
            services = listOf(
                service("IOrderManager"),
                service("IPricingEngine"),
                service("IProductAccess"),
                service("IActuator", ServiceKind.UTILITY),
                service("INotifications"),
            ),
            listeners = listOf(
                ProtocolListenerDescription("rsocket", "0.0.0.0", 7000),
                ProtocolListenerDescription("json-rpc", "127.0.0.1", 7001, "admin"),
            ),
        )

        assertEquals(
            """
            Order System
            ════════════

            Business Logic · Managers
            ┌──────────────────┐
            │IOrderManager     │
            └──────────────────┘

            Business Logic · Engines
            ┌──────────────────┐
            │IPricingEngine    │
            └──────────────────┘

            Resource Access
            ┌──────────────────┐
            │IProductAccess    │
            └──────────────────┘

            Utilities
            ┌──────────────────┐
            │IActuator         │
            └──────────────────┘

            Services
            ┌──────────────────┐
            │INotifications    │
            └──────────────────┘

            rsocket://0.0.0.0:7000  ·  json-rpc://127.0.0.1:7001 (admin)
            """.trimIndent(),
            catalog.renderStartupDiagram(color = false),
        )
    }

    @Test
    fun `renders each service as a colored box`() {
        val rendered = ServiceCatalog(
            name = "Host",
            services = listOf(service("IOrderManager"), service("IActuator", ServiceKind.UTILITY)),
        ).renderStartupDiagram()

        assertTrue(rendered.contains("\u001B[48;2;255;220;115m"))
        assertTrue(rendered.contains("\u001B[48;2;222;198;232m"))
        assertEquals(6, rendered.split("\u001B[0m").size - 1)
    }

    @Test
    fun `omits empty architecture layers and listener footer`() {
        val rendered = ServiceCatalog("Empty Host", emptyList()).renderStartupDiagram(color = false)

        assertEquals("Empty Host\n════════════", rendered)
        assertFalse(rendered.contains("Business Logic"))
        assertFalse(rendered.contains("://"))
    }

    private fun service(name: String, kind: ServiceKind = ServiceKind.SERVICE) = ServiceDescription(
        name = name,
        address = "test.$name",
        kind = kind,
        operations = emptyList(),
        types = emptyList(),
    )
}
