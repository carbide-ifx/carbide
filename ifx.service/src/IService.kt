package ifx.service

import ifx.logging.Log
import ifx.logging.LogTag
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.serialization.Serializable

interface IService {
    val log: Log
        get() = ServiceLoggers.forClass(
            this::class.qualifiedName ?: this::class.simpleName ?: "IService",
        )
}

@OptIn(ExperimentalAtomicApi::class)
private object ServiceLoggers {
    private val loggers = AtomicReference<Map<String, Log>>(emptyMap())

    fun forClass(serviceClassName: String): Log {
        while (true) {
            val current = loggers.load()
            current[serviceClassName]?.let { return it }
            val logger = Log(LogTag(serviceClassName = serviceClassName))
            if (loggers.compareAndSet(current, current + (serviceClassName to logger))) return logger
        }
    }
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
