package ifx.service

import ifx.logging.Log
import kotlinx.serialization.Serializable

interface IService {
    val log: Log
        get() = Log(this::class.qualifiedName ?: this::class.simpleName ?: "IService")

    suspend fun status() = Status(isReady(), isLive())

    suspend fun init() = Unit

    suspend fun isReady(): Boolean = true

    suspend fun isLive(): Boolean = true

}

@Serializable
data class Status(val ready: Boolean, val live: Boolean)

interface IUtility : IService
