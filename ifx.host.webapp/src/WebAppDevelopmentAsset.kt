package ifx.host.webapp

/** Reads a development webapp asset from disk, or returns null when it is not available. */
internal expect fun readDevelopmentWebAsset(path: String): ByteArray?
