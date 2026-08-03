package ifx.context

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import kotlin.coroutines.CoroutineContext

/** Immutable serializable values associated with and propagated from the current call. */
@Serializable(with = ContextSerializer::class)
class Context @PublishedApi internal constructor(
    @PublishedApi internal val elements: Map<String, JsonElement>,
) : CoroutineContext.Element {
    constructor() : this(emptyMap())

    val isEmpty: Boolean get() = elements.isEmpty()

    override val key: CoroutineContext.Key<Context> get() = Key

    companion object Key : CoroutineContext.Key<Context> {
        const val HEADER_KEY = "ifx.context"

        val Empty: Context = Context()

        suspend fun currentOrNull(): Context? = currentCoroutineContext()[Key]

        suspend fun current(): Context = currentOrNull() ?: Empty

        @Deprecated("Use Context.current()", ReplaceWith("Context.current()"))
        suspend fun get(): Context = current()
    }
}

inline fun <reified T : Any> Context.getOrNull(): T? {
    val serializer = serializer<T>()
    val element = elements[serializer.descriptor.serialName] ?: return null
    return ContextFormat.decodeFromJsonElement(serializer, element)
}

inline fun <reified T : Any> Context.set(element: T): Context {
    val serializer = serializer<T>()
    val serialized = ContextFormat.encodeToJsonElement(serializer, element)
    return Context(elements.plus(serializer.descriptor.serialName to serialized))
}

@PublishedApi
internal val ContextFormat = Json {
    encodeDefaults = true
    prettyPrint = false
}

/** Serializes [Context] directly as its opaque element map. */
object ContextSerializer : KSerializer<Context> {
    private val delegate = MapSerializer(String.serializer(), JsonElement.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Context) {
        delegate.serialize(encoder, value.elements)
    }

    override fun deserialize(decoder: Decoder): Context = Context(delegate.deserialize(decoder))
}
