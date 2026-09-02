package ifx.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Structured identity carried through Kermit's String tag. */
@Serializable
data class LogTag(
    val serviceInterface: String? = null,
    val serviceClassName: String? = null,
    val path: List<String> = emptyList(),
    /** Whether human-oriented writers should render a tag prefix for this entry. */
    val display: Boolean = true,
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("span_id") val spanId: String? = null,
    @SerialName("trace_flags") val traceFlags: String? = null,
    /** Whether retention-oriented writers should store this entry. */
    val retained: Boolean = true,
) {
    internal fun displayTag(): String = if (!display) "" else buildList {
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
