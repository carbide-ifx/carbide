package ifx.host.tooling

/** Reads a development UI asset from disk, or returns null when it is not available. */
internal expect fun readTestUiDevelopmentAsset(path: String): String?
