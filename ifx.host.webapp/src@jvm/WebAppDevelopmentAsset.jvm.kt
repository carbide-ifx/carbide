package ifx.host.webapp

import java.io.File

internal actual fun readDevelopmentWebAsset(path: String): ByteArray? =
    File(path).takeIf(File::isFile)?.readBytes()
