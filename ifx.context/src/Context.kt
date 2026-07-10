package ifx.context

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Context(
    val traceId: String = Uuid.random().toString(),
    val elements: Map<String, String> = emptyMap()
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key

    companion object Key : CoroutineContext.Key<Context> {
        val HEADER_KEY = "ifx.context"
        suspend fun get(): Context = currentCoroutineContext()[Key] ?: Context()
    }
}


inline fun <reified T> Context.getOrNull(): T? = this.elements[typeKey<T>()]?.let { Json.decodeFromString(it) }
inline fun <reified T> Context.set(element: T): Context = copy(elements = elements.plus(typeKey<T>() to Json.encodeToString(element)))

inline fun <reified T> typeKey(): String = T::class.qualifiedName
    ?: error("Context values require a named runtime type")
