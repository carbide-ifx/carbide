package ifx.telemetry.otel.internal

import ifx.protocol.contract.Message
import ifx.protocol.contract.headers
import ifx.protocol.contract.withHeader
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

internal data class ActiveSpan(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
    val traceState: String?,
    val isRemote: Boolean = false,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<ActiveSpan> get() = Key

    companion object Key : CoroutineContext.Key<ActiveSpan>
}

internal const val TRACEPARENT_HEADER = "traceparent"
internal const val TRACESTATE_HEADER = "tracestate"

private val traceParentPattern = Regex("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")

internal data class RemoteTraceParent(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
    val traceState: String?,
)

internal fun Message.traceParentOrNull(): RemoteTraceParent? {
    val headers = headers()
    val value = headers.stringValue(TRACEPARENT_HEADER) ?: return null
    val match = traceParentPattern.matchEntire(value) ?: return null
    val (traceId, spanId, traceFlags) = match.destructured
    if (traceId.all { it == '0' } || spanId.all { it == '0' }) return null
    return RemoteTraceParent(traceId, spanId, traceFlags, headers.stringValue(TRACESTATE_HEADER))
}

internal fun Message.withTraceParent(span: ActiveSpan): Message =
    withHeader(TRACEPARENT_HEADER, JsonPrimitive("00-${span.traceId}-${span.spanId}-${span.traceFlags}"))
        .withHeader(TRACESTATE_HEADER, span.traceState?.let(::JsonPrimitive))

internal fun newTraceId(): String = randomHex(byteCount = 16)

internal fun newSpanId(): String = randomHex(byteCount = 8)

internal fun String.isSampled(): Boolean = toIntOrNull(16)?.and(1) == 1

private fun Map<String, *>.stringValue(name: String): String? = entries
    .firstOrNull { (key) -> key.equals(name, ignoreCase = true) }
    ?.value
    ?.let { it as? JsonPrimitive }
    ?.content

private fun randomHex(byteCount: Int): String {
    var bytes: ByteArray
    do {
        bytes = Random.nextBytes(byteCount)
    } while (bytes.all { it == 0.toByte() })
    return buildString(byteCount * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
