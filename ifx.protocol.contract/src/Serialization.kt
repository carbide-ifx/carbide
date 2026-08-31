package ifx.protocol.contract

import ifx.context.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

val RpcFormat = kotlinx.serialization.json.Json {
    encodeDefaults = true
    prettyPrint = false
    ignoreUnknownKeys = true
}

suspend inline fun <reified T> T.encodeToMessage(): Message = Message(
    header = "{}",
    body = RpcFormat.encodeToString(this),
)

suspend fun emptyMessage(): Message = Message(
    header = "{}",
    body = "",
)

inline fun <reified T> Message.decode(): T = RpcFormat.decodeFromString(body)

fun Message.headers(): Map<String, JsonElement> = try {
    RpcFormat.decodeFromString(header.ifEmpty { "{}" })
} catch (exception: Exception) {
    throw ProtocolException(exception) { "Failed to parse message headers: ${exception.message}" }
}

fun Message.withHeader(key: String, value: JsonElement?): Message {
    val headers = headers().toMutableMap()
    if (value == null) headers.remove(key) else headers[key] = value
    return copy(header = RpcFormat.encodeToString(headers))
}

fun Message.contextOrNull(): Context? = try {
    headers()[Context.HEADER_KEY]?.let(RpcFormat::decodeFromJsonElement)
} catch (exception: ProtocolException) {
    throw exception
} catch (exception: Exception) {
    throw ProtocolException(exception) { "Failed to read message context: ${exception.message}" }
}

fun Message.context(): Context = contextOrNull() ?: Context.Empty

fun Message.withContext(context: Context?): Message =
    withHeader(Context.HEADER_KEY, context?.let(RpcFormat::encodeToJsonElement))
