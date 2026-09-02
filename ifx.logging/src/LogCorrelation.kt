package ifx.logging

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/** Trace identifiers that can be attached to structured logs for trace/log correlation. */
data class LogCorrelation(
    val traceId: String,
    val spanId: String,
    val traceFlags: String,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<LogCorrelation> get() = Key

    companion object Key : CoroutineContext.Key<LogCorrelation> {
        suspend fun currentOrNull(): LogCorrelation? = currentCoroutineContext()[Key]
    }

}
