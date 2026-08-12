package ifx.host.webapp

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import ifx.host.ProtocolListener
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

data class WebAppAsset(
    val content: ByteArray,
    val contentType: ContentType,
    val developmentPath: String? = null,
) {
    companion object {
        fun text(
            content: String,
            contentType: ContentType,
            developmentPath: String? = null,
        ): WebAppAsset = WebAppAsset(content.encodeToByteArray(), contentType, developmentPath)
    }
}

/** Hosts a self-contained web application on an existing host listener. */
class WebApp(
    override val listener: ProtocolListener,
    assets: Map<String, WebAppAsset>,
    mountPath: String = "/",
    private val indexAsset: String = "index.html",
    private val developmentDirectory: String? = null,
    private val embeddedCacheControl: String = "public, max-age=3600",
) : HostExtension {
    private val mountPath = normalizeMountPath(mountPath)
    private val assets = assets.mapKeys { (path, _) -> normalizeAssetPath(path) }
    private val versionPath = routeFor(".ifx/webapp-version")

    init {
        require(this.assets.isNotEmpty()) { "A webapp must contain at least one asset" }
        require(normalizeAssetPath(indexAsset) in this.assets) {
            "Webapp index asset is not present: $indexAsset"
        }
    }

    override fun install(application: Application, context: HostExtensionContext) {
        application.routing {
            assets.forEach { (path, embedded) ->
                val route = if (path == normalizeAssetPath(indexAsset)) mountPath else routeFor(path)
                get(route) {
                    val development = developmentDirectory?.let { directory ->
                        readDevelopmentWebAsset(
                            "${directory.trimEnd('/', '\\')}/${embedded.developmentPath(path)}",
                        )
                    }
                    call.response.headers.append(
                        HttpHeaders.CacheControl,
                        if (developmentDirectory == null) embeddedCacheControl else "no-store",
                    )
                    val content = development ?: embedded.content
                    call.respondBytes(
                        if (path == normalizeAssetPath(indexAsset) && developmentDirectory != null) {
                            injectLiveReload(content.decodeToString()).encodeToByteArray()
                        } else {
                            content
                        },
                        embedded.contentType,
                    )
                }
            }
            if (developmentDirectory != null) {
                get(versionPath) {
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                    val version = assets.entries.fold(1) { result, (path, embedded) ->
                        31 * result + (
                            readDevelopmentWebAsset(
                                "${developmentDirectory.trimEnd('/', '\\')}/${embedded.developmentPath(path)}",
                            ) ?: embedded.content
                            ).contentHashCode()
                    }
                    call.respondBytes(version.toString().encodeToByteArray(), ContentType.Text.Plain)
                }
            }
        }
    }

    private fun routeFor(assetPath: String): String =
        if (mountPath == "/") "/$assetPath" else "$mountPath/$assetPath"

    private fun injectLiveReload(html: String): String {
        val script = """
            <script>
              (() => {
                let currentVersion;
                const checkForChanges = async () => {
                  try {
                    const response = await fetch("$versionPath", { cache: "no-store" });
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
        return html.replace("</body>", "$script\n</body>")
    }
}

private fun WebAppAsset.developmentPath(assetPath: String): String =
    normalizeAssetPath(developmentPath ?: assetPath)

private fun normalizeMountPath(path: String): String {
    require(path.startsWith('/')) { "Webapp mount path must start with /" }
    require(".." !in path.split('/')) { "Webapp mount path must not contain .." }
    return path.trimEnd('/').ifEmpty { "/" }
}

private fun normalizeAssetPath(path: String): String {
    val normalized = path.trimStart('/')
    require(normalized.isNotEmpty()) { "Webapp asset path must not be empty" }
    require(".." !in normalized.split('/')) { "Webapp asset path must not contain ..: $path" }
    return normalized
}
