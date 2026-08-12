package ifx.host.tooling

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import ifx.host.ProtocolListener
import ifx.host.webapp.WebApp
import ifx.host.webapp.WebAppAsset
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import io.ktor.http.ContentType
import io.ktor.server.application.Application

class ServiceExplorer(
    override val listener: ProtocolListener,
    developmentDirectory: String? = null,
) : HostExtension {
    private val webApp = WebApp(
        listener = listener,
        assets = mapOf(
            "index.html" to WebAppAsset.text(TestUiAssets.html, ContentType.Text.Html),
            "ifx/test-ui.js" to WebAppAsset.text(
                TestUiAssets.javascript,
                ContentType.Application.JavaScript,
                developmentPath = "test-ui.js",
            ),
        ),
        developmentDirectory = developmentDirectory,
    )

    init {
        require(listener.protocol.id == RSOCKET_PROTOCOL_ID) {
            "ServiceExplorer requires an RSocket listener"
        }
    }

    override fun install(application: Application, context: HostExtensionContext) {
        webApp.install(application, context)
    }
}
