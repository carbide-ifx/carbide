@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ifx.host.webapp

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind

internal actual fun Application.installWebAppDirectory(
    directory: String,
    mountPath: String,
    indexFile: String,
) {
    require(webAppFileExists("${directory.trimEnd('/', '\\')}/$indexFile")) {
        "Webapp index file does not exist: ${directory.trimEnd('/', '\\')}/$indexFile"
    }
    routing {
        get(mountPath) {
            call.respondWebAppFile(directory, indexFile)
        }
        get(if (mountPath == "/") "/{path...}" else "$mountPath/{path...}") {
            val relativePath = call.parameters.getAll("path")
                ?.takeIf(::isSafeWebAppPath)
                ?.joinToString("/")
                ?: return@get call.respondBytes(ByteArray(0), status = io.ktor.http.HttpStatusCode.NotFound)
            call.respondWebAppFile(directory, relativePath)
        }
    }
}

private fun webAppFileExists(path: String): Boolean {
    val file = fopen(path, "rb") ?: return false
    fclose(file)
    return true
}

private suspend fun io.ktor.server.application.ApplicationCall.respondWebAppFile(
    directory: String,
    relativePath: String,
) {
    val bytes = readWebAppFile("${directory.trimEnd('/', '\\')}/$relativePath")
        ?: return respondBytes(ByteArray(0), status = io.ktor.http.HttpStatusCode.NotFound)
    respondBytes(bytes, contentType(relativePath))
}

private fun isSafeWebAppPath(segments: List<String>): Boolean =
    segments.isNotEmpty() && segments.none { it.isBlank() || it == "." || it == ".." }

private fun contentType(path: String): ContentType = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> ContentType.Text.Html
    "css" -> ContentType.Text.CSS
    "js", "mjs" -> ContentType.parse("text/javascript")
    "json", "map" -> ContentType.Application.Json
    "txt" -> ContentType.Text.Plain
    "svg" -> ContentType.parse("image/svg+xml")
    "png" -> ContentType.Image.PNG
    "jpg", "jpeg" -> ContentType.Image.JPEG
    "gif" -> ContentType.Image.GIF
    "webp" -> ContentType.parse("image/webp")
    "ico" -> ContentType.parse("image/x-icon")
    "woff" -> ContentType.parse("font/woff")
    "woff2" -> ContentType.parse("font/woff2")
    "ttf" -> ContentType.parse("font/ttf")
    "wasm" -> ContentType.parse("application/wasm")
    else -> ContentType.Application.OctetStream
}

private fun readWebAppFile(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    return try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val length = ftell(file)
        if (length < 0) return null
        rewind(file)
        val bytes = ByteArray(length.toInt())
        if (bytes.isNotEmpty()) {
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
            if (read.toLong() != length) return null
        }
        bytes
    } finally {
        fclose(file)
    }
}
