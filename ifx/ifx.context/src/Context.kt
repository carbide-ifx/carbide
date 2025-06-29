package ifx.context

import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {

}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Context(
    val traceId: String = Uuid.random().toString(),
    val number: Int = 0
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key
    companion object Key : CoroutineContext.Key<Context> {
        val HEADER_KEY = "ifx.context"
    }
}
