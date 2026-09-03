package ifx.host

import ifx.protocol.contract.ProtocolListenerDescription
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceKind

private const val DIAGRAM_WIDTH = 100
private const val MINIMUM_CARD_WIDTH = 18
private const val RESET = "\u001B[0m"

internal fun ServiceCatalog.renderStartupDiagram(color: Boolean = true): String = buildString {
    appendLine(name)
    appendLine("═".repeat(name.length.coerceAtLeast(12)))

    val classified = services.groupBy(ServiceDescription::architectureLayer)
    ArchitectureLayer.entries.forEach { layer ->
        val layerServices = classified[layer].orEmpty()
        if (layerServices.isNotEmpty()) {
            appendLine()
            appendLine(layer.label)
            renderCards(layerServices, layer, color)
        }
    }

    if (listeners.isNotEmpty()) {
        appendLine()
        appendLine(listeners.joinToString("  ·  ", transform = ProtocolListenerDescription::displayAddress))
    }
}.trimEnd()

private fun StringBuilder.renderCards(
    services: List<ServiceDescription>,
    layer: ArchitectureLayer,
    color: Boolean,
) {
    val cards = services.map { service -> ServiceCard(service.name, layer, color) }
    val rows = mutableListOf<MutableList<ServiceCard>>()
    cards.forEach { card ->
        val row = rows.lastOrNull()
        val rowWidth = row?.sumOf(ServiceCard::width)?.plus((row.size - 1) * 2) ?: 0
        if (row == null || rowWidth + 2 + card.width > DIAGRAM_WIDTH) {
            rows += mutableListOf(card)
        } else {
            row += card
        }
    }

    rows.forEach { row ->
        appendLine(row.joinToString("  ") { it.top })
        appendLine(row.joinToString("  ") { it.middle })
        appendLine(row.joinToString("  ") { it.bottom })
    }
}

private data class ServiceCard(
    val name: String,
    val layer: ArchitectureLayer,
    val color: Boolean,
) {
    private val contentWidth = name.length.coerceAtLeast(MINIMUM_CARD_WIDTH)
    val width = contentWidth + 2

    val top: String get() = painted("┌${"─".repeat(contentWidth)}┐")
    val middle: String get() = painted("│${name.padEnd(contentWidth)}│")
    val bottom: String get() = painted("└${"─".repeat(contentWidth)}┘")

    private fun painted(text: String): String = if (color) "${layer.ansi}$text$RESET" else text
}

private enum class ArchitectureLayer(
    val label: String,
    val ansi: String,
) {
    MANAGER("Business Logic · Managers", "\u001B[38;2;28;33;30m\u001B[48;2;255;220;115m"),
    ENGINE("Business Logic · Engines", "\u001B[38;2;28;33;30m\u001B[48;2;255;150;53m"),
    ACCESS("Resource Access", "\u001B[38;2;28;33;30m\u001B[48;2;227;228;227m"),
    UTILITY("Utilities", "\u001B[38;2;28;33;30m\u001B[48;2;222;198;232m"),
    UNCLASSIFIED("Services", "\u001B[38;2;28;33;30m\u001B[48;2;222;198;232m"),
}

private fun ServiceDescription.architectureLayer(): ArchitectureLayer {
    if (kind == ServiceKind.UTILITY) return ArchitectureLayer.UTILITY
    val contractName = name.removeInterfacePrefix()
    return when {
        contractName.endsWith("Manager", ignoreCase = true) -> ArchitectureLayer.MANAGER
        contractName.endsWith("Engine", ignoreCase = true) -> ArchitectureLayer.ENGINE
        contractName.endsWith("Access", ignoreCase = true) -> ArchitectureLayer.ACCESS
        else -> ArchitectureLayer.UNCLASSIFIED
    }
}

private fun String.removeInterfacePrefix(): String =
    if (length > 1 && first() == 'I' && this[1].isUpperCase()) drop(1) else this

private fun ProtocolListenerDescription.displayAddress(): String =
    "$protocolId://$host:$port" + if (listenerId == protocolId) "" else " ($listenerId)"
