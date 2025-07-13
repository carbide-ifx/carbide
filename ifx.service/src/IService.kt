package ifx.service

import ifx.logging.Log


interface IService {
    suspend fun status() = Status(isReady(), isLive())

    suspend fun init() = Unit

    suspend fun isReady(): Boolean = true

    suspend fun isLive(): Boolean = true

    val log get() = Log(this)
}

data class Status(val ready: Boolean, val live: Boolean)
