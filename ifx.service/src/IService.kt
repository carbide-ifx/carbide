package ifx.service

import kotlinx.serialization.Serializable

interface IService {
    suspend fun status() = Status(isReady(), isLive())

    suspend fun init() = Unit

    suspend fun isReady(): Boolean = true

    suspend fun isLive(): Boolean = true

}

@Serializable
data class Status(val ready: Boolean, val live: Boolean)
