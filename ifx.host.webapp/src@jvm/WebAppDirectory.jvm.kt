package ifx.host.webapp

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import java.io.File

internal actual fun Application.installWebAppDirectory(
    directory: String,
    mountPath: String,
    indexFile: String,
) {
    val root = File(directory)
    require(root.isDirectory) { "Webapp directory does not exist: ${root.absolutePath}" }
    val index = File(root, indexFile)
    require(index.isFile) { "Webapp index file does not exist: ${index.absolutePath}" }
    routing {
        staticFiles(
            remotePath = mountPath,
            dir = root,
            index = indexFile,
        )
    }
}
