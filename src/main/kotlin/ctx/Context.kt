package ctx

import io.grpc.Metadata
import io.grpc.Metadata.AsciiMarshaller
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

@Serializable
data class Context(val data: String) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key

    companion object Key : CoroutineContext.Key<Context> {
        val CUSTOM_HEADER_KEY: Metadata.Key<Context> = Metadata.Key.of("custom_server_header_key", ContextStringMarshaller)
        val EMPTY = Context("")
    }
    object ContextStringMarshaller : AsciiMarshaller<Context> {
        override fun toAsciiString(value: Context): String = Json.encodeToString(value)
        override fun parseAsciiString(serialized: String): Context = Json.decodeFromString(serialized)
    }

}
