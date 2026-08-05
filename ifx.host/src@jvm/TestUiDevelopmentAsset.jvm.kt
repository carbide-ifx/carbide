package ifx.host

import java.io.File

internal actual fun readTestUiDevelopmentAsset(path: String): String? =
    File(path).takeIf(File::isFile)?.readText()
