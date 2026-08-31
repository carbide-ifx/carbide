package ifx.host.webapp

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import io.ktor.server.application.Application

/** Hosts a web application built into a filesystem directory. */
class WebApp(
    directory: String,
    mountPath: String = "/",
    indexFile: String = "index.html",
) : HostExtension {
    private val directory = directory.also {
        require(it.isNotBlank()) { "Webapp directory must not be blank" }
    }
    private val mountPath = normalizeMountPath(mountPath)
    private val indexFile = normalizeAssetPath(indexFile)

    override fun install(application: Application, context: HostExtensionContext) {
        application.installWebAppDirectory(directory, mountPath, indexFile)
    }
}

private fun normalizeMountPath(path: String): String {
    require(path.startsWith('/')) { "Webapp mount path must start with /" }
    require(".." !in path.split('/')) { "Webapp mount path must not contain .." }
    return path.trimEnd('/').ifEmpty { "/" }
}

private fun normalizeAssetPath(path: String): String {
    val normalized = path.trimStart('/')
    require(normalized.isNotEmpty()) { "Webapp index file must not be empty" }
    require(".." !in normalized.split('/')) { "Webapp index file must not contain ..: $path" }
    return normalized
}
