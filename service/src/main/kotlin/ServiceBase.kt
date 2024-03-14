package ifx.service

import ifx.context.Context
import kotlinx.coroutines.currentCoroutineContext

open class ServiceBase {
    suspend fun getContext() = currentCoroutineContext()[Context] ?: Context()
}
