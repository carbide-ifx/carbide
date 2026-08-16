package ifx.service.explorer

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import ifx.host.ProtocolListener
import ifx.host.webapp.WebApp
import ifx.protocol.rsocket.RSOCKET_PROTOCOL_ID
import io.ktor.server.application.Application

class ServiceExplorer(
    override val listener: ProtocolListener,
    directory: String,
) : HostExtension {
    init {
        require(listener.protocol.id == RSOCKET_PROTOCOL_ID) {
            "ServiceExplorer requires an RSocket listener"
        }
    }

    private val webApp = WebApp(
        listener = listener,
        directory = directory,
    )

    override fun install(application: Application, context: HostExtensionContext) {
        webApp.install(application, context)
    }
}
