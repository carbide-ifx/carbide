package ifx.logging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Structured identity carried through Kermit's String tag. */
@Serializable
data class LogTag(
    val serviceInterface: String? = null,
    val serviceClassName: String? = null,
    val path: List<String> = emptyList(),
) {
    internal fun displayTag(): String = buildList {
        serviceClassName
            ?.substringAfterLast('.')
            ?.takeIf(String::isNotBlank)
            ?.let(::add)
            ?: serviceInterface
                ?.substringAfterLast('.')
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        addAll(path.filter(String::isNotBlank))
    }.joinToString(".")
}

object LogTagCodec {
    private const val PREFIX = "@ifx-log:1:"
    private val format = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    fun encode(tag: LogTag): String = PREFIX + format.encodeToString(tag)

    fun decodeOrNull(tag: String): LogTag? {
        if (!tag.startsWith(PREFIX)) return null
        return try {
            format.decodeFromString<LogTag>(tag.removePrefix(PREFIX))
        } catch (_: Exception) {
            null
        }
    }
}
