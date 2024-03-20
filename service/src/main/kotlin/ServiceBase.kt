package ifx.service

import ifx.context.Context
import kotlinx.coroutines.currentCoroutineContext

abstract class ServiceBase {
    suspend fun getContext() = currentCoroutineContext()[Context] ?: Context()

    fun status() = Status(isReady(), isLive())

    open fun init() = Unit

    open fun isReady(): Boolean = true

    open fun isLive(): Boolean = true

    data class Status(val ready: Boolean, val live: Boolean)
}
