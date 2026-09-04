package ifx.service.explorer

private const val RESOURCE_ROOT = "ifx/service/explorer"

internal actual fun bundledServiceExplorerAsset(path: String): ByteArray {
    val resourcePath = "$RESOURCE_ROOT/$path"
    return checkNotNull(ServiceExplorer::class.java.classLoader.getResourceAsStream(resourcePath)) {
        "Bundled Service Explorer asset does not exist: $resourcePath"
    }.use { it.readAllBytes() }
}
