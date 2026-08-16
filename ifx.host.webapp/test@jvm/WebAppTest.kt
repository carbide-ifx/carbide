package ifx.host.webapp

import ifx.host.HostExtensionContext
import ifx.host.IServerProtocol
import ifx.host.ProtocolListener
import ifx.protocol.contract.Endpoint
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class WebAppTest {
    @Test
    fun `serves an index and nested assets from a directory`() = withWebAppDirectory { directory ->
        directory.resolve("index.html").writeText("<h1>Explorer</h1>")
        directory.resolve("assets").createDirectories()
        directory.resolve("assets/app.js").writeText("console.log('ready')")

        testApplication {
            application {
                WebApp(ProtocolListener(TestProtocol), directory.toString()).install(
                    this,
                    HostExtensionContext("test", emptyList(), { emptyList() }),
                )
            }

            assertEquals("<h1>Explorer</h1>", client.get("/").bodyAsText())
            val javascript = client.get("/assets/app.js")
            assertEquals(HttpStatusCode.OK, javascript.status)
            assertEquals(ContentType.parse("text/javascript"), javascript.contentType()?.withoutParameters())
            assertEquals("console.log('ready')", javascript.bodyAsText())
            assertEquals(HttpStatusCode.NotFound, client.get("/missing.js").status)
        }
    }

    @Test
    fun `mounts a webapp below the listener root`() = withWebAppDirectory { directory ->
        directory.resolve("index.html").writeText("mounted")

        testApplication {
            application {
                WebApp(
                    listener = ProtocolListener(TestProtocol),
                    directory = directory.toString(),
                    mountPath = "/explorer",
                ).install(this, HostExtensionContext("test", emptyList(), { emptyList() }))
            }

            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
            assertEquals("mounted", client.get("/explorer").bodyAsText())
        }
    }
}

private inline fun withWebAppDirectory(block: (java.nio.file.Path) -> Unit) {
    val directory = createTempDirectory("ifx-webapp-")
    try {
        block(directory)
    } finally {
        directory.toFile().deleteRecursively()
    }
}

private object TestProtocol : IServerProtocol {
    override val id: String = "test"

    override fun install(application: Application, endpoints: List<Endpoint>) = Unit
}
