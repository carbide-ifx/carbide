package ifx.protocol.contract

import ifx.context.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

val RpcFormat = kotlinx.serialization.json.Json {
    encodeDefaults = true
    prettyPrint = false
}

suspend inline fun <reified T> T.encodeToMessage(): Message = Message(
    header = RpcFormat.encodeToString(
        mapOf(Context.HEADER_KEY to currentCoroutineContext()[Context]).mapNotNullValues()
    ),
    body = RpcFormat.encodeToString(this),
)

suspend fun emptyMessage(): Message = Message(
    header = RpcFormat.encodeToString(
        mapOf(Context.HEADER_KEY to currentCoroutineContext()[Context]).mapNotNullValues()
    ),
    body = "",
)

inline fun <reified T> Message.decode(): T = RpcFormat.decodeFromString(body)

fun Message.parseContext(): Context = try {
    val headers: Map<String, JsonObject> = RpcFormat.decodeFromString(header.ifEmpty { "{}" })
    headers[Context.HEADER_KEY]?.let(RpcFormat::decodeFromJsonElement) ?: Context()
} catch (exception: Exception) {
    throw ProtocolException(exception) { "Host - Failed to parse context: ${exception.message}" }
}

@PublishedApi
internal fun <K, V> Map<K, V?>.mapNotNullValues(): Map<K, V> =
    mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
