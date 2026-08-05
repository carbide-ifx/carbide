package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.ProtocolListenerDescription
import ifx.protocol.contract.ServiceCatalog
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString

internal fun Application.installHostTooling(
    hostName: String,
    endpoints: List<Endpoint>,
    config: HostTooling,
    listeners: () -> List<BoundProtocolListener>,
) {
    val developmentAssetPath = config.developmentDirectory?.let { directory ->
        "${directory.trimEnd('/', '\\')}/test-ui.js"
    }
    routing {
        get("/") {
            if (developmentAssetPath != null) call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondText(testUiHtml(developmentAssetPath), ContentType.Text.Html)
        }
        get("/ifx/services") {
            call.respondText(
                RpcFormat.encodeToString(
                    ServiceCatalog(
                        name = hostName,
                        services = endpoints.map(Endpoint::description),
                        listeners = listeners().map { listener ->
                            ProtocolListenerDescription(
                                protocolId = listener.protocolId,
                                host = listener.host,
                                port = listener.port,
                            )
                        },
                    ),
                ),
                ContentType.Application.Json,
            )
        }
        get("/ifx/test-ui.js") {
            if (developmentAssetPath != null) call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respondText(
                developmentAssetPath?.let(::readTestUiDevelopmentAsset) ?: TestUiAssets.javascript,
                ContentType.Application.JavaScript,
            )
        }
        if (developmentAssetPath != null) {
            get("/ifx/test-ui-version") {
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respondText(
                    readTestUiDevelopmentAsset(developmentAssetPath)?.hashCode()?.toString() ?: "missing",
                    ContentType.Text.Plain,
                )
            }
        }
    }
}

private fun testUiHtml(developmentAssetPath: String?): String {
    if (developmentAssetPath == null) return TestUiAssets.html

    val liveReloadScript = """
        <script>
          (() => {
            let currentVersion;
            const checkForChanges = async () => {
              try {
                const response = await fetch("/ifx/test-ui-version", { cache: "no-store" });
                if (response.ok) {
                  const nextVersion = await response.text();
                  if (currentVersion === undefined) currentVersion = nextVersion;
                  else if (nextVersion !== currentVersion) location.reload();
                }
              } catch {}
              setTimeout(checkForChanges, 500);
            };
            checkForChanges();
          })();
        </script>
    """.trimIndent()

    return TestUiAssets.html.replace("</body>", "$liveReloadScript\n</body>")
}
