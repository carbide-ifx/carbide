package ifx.build.gateway

import ifx.gateway.contract.GatewayProjection
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GatewayArtifactsTest {
    @Test
    fun `renders one conventional artifact directory per projection version`() {
        val artifacts = renderGatewayArtifacts(listOf(projection("product-web", 2)))

        assertEquals(
            listOf("product-web/v2/client.ts", "product-web/v2/openapi.json"),
            artifacts.map { artifact -> artifact.relativePath.toString() },
        )
        assertTrue(artifacts.first().content.contains("ProductWebClient"))
        assertTrue(artifacts.last().content.contains("\"version\":\"2.0.0\""))
    }

    @Test
    fun `rejects two projections with the same public address`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            renderGatewayArtifacts(listOf(projection("product-web"), projection("product-web")))
        }

        assertEquals("Duplicate gateway projection address: product-web", failure.message)
    }

    @Test
    fun `rejects an artifact path outside the task output before replacing existing output`() {
        val temporary = createTempDirectory("ifx-gateway-artifacts")
        try {
            val output = temporary.resolve("gateway").createDirectories()
            val sentinel = output.resolve("keep.txt")
            sentinel.writeText("keep")

            assertFailsWith<IllegalArgumentException> {
                writeGatewayArtifacts(
                    output,
                    listOf(GatewayArtifact(Path.of("..", "escaped.txt"), "unsafe")),
                )
            }

            assertTrue(sentinel.exists())
            assertTrue(!temporary.resolve("escaped.txt").exists())
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }
}

private fun projection(name: String, version: Int? = null): GatewayProjection =
    object : GatewayProjection {
        override val name: String = name
        override val version: Int? = version
        override val services = emptyList<ifx.gateway.contract.GatewayServiceProjection>()
    }
