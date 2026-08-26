package ifx.service

import ifx.logging.Log
import kotlinx.serialization.Serializable

interface IService {
    val log: Log
        get() = Log(this::class.qualifiedName ?: this::class.simpleName ?: "IService")
}

/** Host-local lifecycle implemented by service instances, never inherited by their RPC contracts. */
interface IServiceLifecycle {
    suspend fun start() = Unit

    suspend fun stop() = Unit

    suspend fun health(): ServiceHealth = ServiceHealth.healthy()
}

@Serializable
data class ServiceHealth(
    val ready: Boolean,
    val live: Boolean,
    val detail: String? = null,
) {
    companion object {
        fun healthy(): ServiceHealth = ServiceHealth(ready = true, live = true)
    }
}

interface IUtility : IService
