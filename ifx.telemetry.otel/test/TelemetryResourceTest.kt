package ifx.telemetry.otel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TelemetryResourceTest {
    @Test
    fun `application attributes are defensively copied`() {
        val attributes = mutableMapOf(
            "cloud.region" to "eu-west-1",
            "cloud.provider" to "aws",
        )
        val resource = TelemetryResource("sales-service", attributes = attributes)

        attributes["cloud.region"] = "changed"

        assertEquals("eu-west-1", resource.attributes["cloud.region"])
    }

    @Test
    fun `typed identity takes precedence over application attributes`() {
        val resource = TelemetryResource(
            serviceName = "sales-service",
            serviceNamespace = "commerce",
            attributes = mapOf(
                "service.name" to "wrong-service",
                "service.namespace" to "wrong-namespace",
            ),
        )

        assertEquals("sales-service", resource.otelAttributes()["service.name"])
        assertEquals("commerce", resource.otelAttributes()["service.namespace"])
    }

    @Test
    fun `resources have value equality`() {
        val first = TelemetryResource("sales-service", attributes = mapOf("cloud.region" to "eu-west-1"))
        val second = TelemetryResource("sales-service", attributes = mapOf("cloud.region" to "eu-west-1"))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `service name is required`() {
        assertFailsWith<IllegalArgumentException> { TelemetryResource(" ") }
    }
}
