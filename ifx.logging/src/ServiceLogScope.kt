package ifx.logging

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/** Identity of the service implementation currently executing in this coroutine. */
data class ServiceLogScope(
    val serviceInterface: String,
    val serviceClassName: String,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<ServiceLogScope> get() = Key

    companion object Key : CoroutineContext.Key<ServiceLogScope> {
        suspend fun currentOrNull(): ServiceLogScope? = currentCoroutineContext()[Key]
    }
}
