package ifx.context

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.cast
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
        val CTX: ScopedValue<Context> = ScopedValue.newInstance();
        val HEADER_KEY = "ifx.context"
        suspend fun get(): Context = currentCoroutineContext()[Key] ?: Context()
        fun getBlocking(): Context = if (CTX.isBound) CTX.get() else Context()
    }
}


inline fun <reified T> Context.getOrNull(): T? = this.elements[T::class.java.name]?.let { Json.decodeFromString(it) }
inline fun <reified T> Context.set(element: T): Context = copy(elements = elements.plus(T::class.java.name to Json.encodeToString(element)))
