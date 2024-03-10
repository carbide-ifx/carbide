package ifx.service

import ifx.ctx.Context
import kotlinx.coroutines.currentCoroutineContext

open class ServiceBase {
    suspend fun getContext() = currentCoroutineContext()[Context] ?: Context()
    fun getBlockingContext() = Context.BLOCKING_CONTEXT_KEY.get() ?: Context()

}
