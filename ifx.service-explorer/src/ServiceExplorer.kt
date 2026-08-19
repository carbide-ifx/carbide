package ifx.service.explorer

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import ifx.host.ProtocolListener
import ifx.host.webapp.WebApp
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

class ServiceExplorer(
    override val listener: ProtocolListener,
    developmentDirectory: String? = null,
) : HostExtension {
    init {
        require(listener.protocol.id == RSOCKET_PROTOCOL_ID) {
            "ServiceExplorer requires an RSocket listener"
        }
    }

    private val developmentWebApp = developmentDirectory?.let { directory ->
        WebApp(
            listener = listener,
            directory = directory,
        )
    }

    override fun install(application: Application, context: HostExtensionContext) {
        if (developmentWebApp != null) {
            developmentWebApp.install(application, context)
            return
        }
        application.routing {
            get("/") {
                call.respondBytes(
                    bundledServiceExplorerAsset("index.html"),
                    ContentType.Text.Html,
                )
            }
            get("/test-ui.js") {
                call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
                call.respondBytes(
                    bundledServiceExplorerAsset("test-ui.js.gz"),
                    ContentType.parse("text/javascript"),
                )
            }
        }
    }
}
