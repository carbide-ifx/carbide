package ifx.build.jib

import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class JibTest {
    @Test
    fun `extra directory is copied recursively to its exact container path`() {
        val source = createTempDirectory("ifx-jib-extra")
        source.resolve("index.html").writeText("<h1>Explorer</h1>")
        source.resolve("assets").createDirectory()
        source.resolve("assets/app.js").writeText("console.log('Explorer')")

        val layer = assertNotNull(
            extraDirectoriesLayer(
                listOf(extraDirectory(source, "/app/webapps/service-explorer")),
            ),
        )

        assertEquals(
            setOf(
                "/app/webapps/service-explorer",
                "/app/webapps/service-explorer/index.html",
                "/app/webapps/service-explorer/assets",
                "/app/webapps/service-explorer/assets/app.js",
            ),
            layer.entries.mapTo(mutableSetOf()) { it.extractionPath.toString() },
        )
    }

    @Test
    fun `extra directory must exist`() {
        val missing = createTempDirectory("ifx-jib-missing").resolve("dist")

        val exception = assertFailsWith<IllegalArgumentException> {
            extraDirectoriesLayer(listOf(extraDirectory(missing, "/app/webapps/missing")))
        }

        assertEquals(
            "Extra directory does not exist: ${missing.toAbsolutePath()}",
            exception.message,
        )
    }
}

private fun extraDirectory(source: Path, destination: String): JibExtraDirectory =
    object : JibExtraDirectory {
        override val source: Path = source
        override val destination: String = destination
    }
