@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ifx.host.tooling

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

internal actual fun readTestUiDevelopmentAsset(path: String): String? {
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
        bytes.decodeToString()
    } finally {
        fclose(file)
    }
}
