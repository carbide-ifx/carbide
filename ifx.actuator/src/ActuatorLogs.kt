@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package ifx.actuator

import co.touchlab.kermit.Severity
import ifx.logging.LogTag
import ifx.logging.installLogWriter
import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.atomicArrayOfNulls
import kotlin.concurrent.atomics.updateAt
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlin.time.Clock

const val DEFAULT_ACTUATOR_LOG_CAPACITY = 500

@Serializable
data class ActuatorLogEntry(
    val sequence: Long,
    val timestampEpochMilliseconds: Long,
    val serviceInterface: String,
    val serviceClassName: String?,
    val path: List<String>,
    val severity: ActuatorLogSeverity,
    val message: String,
    val throwable: String?,
)

@Serializable
enum class ActuatorLogSeverity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert,
}

/** Thread-safe in-memory retention of the latest log entries for each service interface. */
class ActuatorLogStore(
    val capacityPerService: Int = DEFAULT_ACTUATOR_LOG_CAPACITY,
) {
    init {
        require(capacityPerService > 0) { "Actuator log capacity must be greater than zero" }
    }

    private val buffers = AtomicReference<Map<String, ServiceLogBuffer>>(emptyMap())

    internal fun append(
        tag: LogTag,
        severity: Severity,
        message: String,
        throwable: Throwable?,
    ) {
        val serviceInterface = tag.serviceInterface ?: return
        bufferFor(serviceInterface).append(
            timestampEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
            tag = tag,
            severity = severity.toActuatorLogSeverity(),
            message = message,
            throwable = throwable?.stackTraceToString(),
        )
    }

    fun logs(serviceInterface: String): List<ActuatorLogEntry> =
        buffers.load()[serviceInterface]?.snapshot().orEmpty()

    fun latest(serviceInterface: String): Flow<ActuatorLogEntry> =
        bufferFor(serviceInterface).latest()

    fun serviceInterfaces(): Set<String> = buffers.load().keys

    private fun bufferFor(serviceInterface: String): ServiceLogBuffer {
        while (true) {
            val current = buffers.load()
            current[serviceInterface]?.let { return it }

            val buffer = ServiceLogBuffer(serviceInterface, capacityPerService)
            if (buffers.compareAndSet(current, current + (serviceInterface to buffer))) return buffer
        }
    }
}

private fun Severity.toActuatorLogSeverity(): ActuatorLogSeverity = when (this) {
    Severity.Verbose -> ActuatorLogSeverity.Verbose
    Severity.Debug -> ActuatorLogSeverity.Debug
    Severity.Info -> ActuatorLogSeverity.Info
    Severity.Warn -> ActuatorLogSeverity.Warn
    Severity.Error -> ActuatorLogSeverity.Error
    Severity.Assert -> ActuatorLogSeverity.Assert
}

object ActuatorLogs {
    private val store = ActuatorLogStore()
    private val writer = ActuatorLogWriter(store)

    fun install() = installLogWriter(writer)

    fun logs(serviceInterface: String): List<ActuatorLogEntry> = store.logs(serviceInterface)

    fun latest(serviceInterface: String): Flow<ActuatorLogEntry> = store.latest(serviceInterface)

    fun serviceInterfaces(): Set<String> = store.serviceInterfaces()
}

private class ServiceLogBuffer(
    private val serviceInterface: String,
    capacity: Int,
) {
    private val sequence = AtomicLong(0L)
    private val entries: AtomicArray<ActuatorLogEntry?> = atomicArrayOfNulls(capacity)
    private val latest = MutableSharedFlow<ActuatorLogEntry>(
        replay = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun append(
        timestampEpochMilliseconds: Long,
        tag: LogTag,
        severity: ActuatorLogSeverity,
        message: String,
        throwable: String?,
    ) {
        val nextSequence = sequence.addAndFetch(1L)
        val entry = ActuatorLogEntry(
            sequence = nextSequence,
            timestampEpochMilliseconds = timestampEpochMilliseconds,
            serviceInterface = serviceInterface,
            serviceClassName = tag.serviceClassName,
            path = tag.path,
            severity = severity,
            message = message,
            throwable = throwable,
        )
        val index = ((nextSequence - 1L) % entries.size).toInt()
        entries.updateAt(index) { current ->
            if (current == null || current.sequence < nextSequence) entry else current
        }
        if (entries.loadAt(index)?.sequence == nextSequence) latest.tryEmit(entry)
    }

    fun latest(): Flow<ActuatorLogEntry> = latest.asSharedFlow()

    fun snapshot(): List<ActuatorLogEntry> = buildList {
        repeat(entries.size) { index ->
            entries.loadAt(index)?.let(::add)
        }
    }.sortedBy(ActuatorLogEntry::sequence)
}
