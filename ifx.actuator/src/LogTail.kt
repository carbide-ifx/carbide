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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

const val DEFAULT_LOG_TAIL_CAPACITY = 500

@Serializable
data class LogTailEntry(
    val sequence: Long,
    val timestampEpochMilliseconds: Long,
    val serviceInterface: String,
    val serviceClassName: String?,
    val path: List<String>,
    val severity: LogTailSeverity,
    val message: String,
    val throwable: String?,
    @SerialName("trace_id") val traceId: String?,
    @SerialName("span_id") val spanId: String?,
    @SerialName("trace_flags") val traceFlags: String?,
)

@Serializable
enum class LogTailSeverity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert,
}

/** Thread-safe in-memory retention of the latest log entries for each service interface. */
class LogTailStore(
    val capacityPerService: Int = DEFAULT_LOG_TAIL_CAPACITY,
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
            severity = severity.toLogTailSeverity(),
            message = message,
            throwable = throwable?.stackTraceToString(),
        )
    }

    fun logs(serviceInterface: String): List<LogTailEntry> =
        buffers.load()[serviceInterface]?.snapshot().orEmpty()

    fun latest(serviceInterface: String): Flow<LogTailEntry> =
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

private fun Severity.toLogTailSeverity(): LogTailSeverity = when (this) {
    Severity.Verbose -> LogTailSeverity.Verbose
    Severity.Debug -> LogTailSeverity.Debug
    Severity.Info -> LogTailSeverity.Info
    Severity.Warn -> LogTailSeverity.Warn
    Severity.Error -> LogTailSeverity.Error
    Severity.Assert -> LogTailSeverity.Assert
}

object LogTail {
    private val store = LogTailStore()
    private val writer = LogTailWriter(store)

    fun install() = installLogWriter(writer)

    fun logs(serviceInterface: String): List<LogTailEntry> = store.logs(serviceInterface)

    fun latest(serviceInterface: String): Flow<LogTailEntry> = store.latest(serviceInterface)

    fun serviceInterfaces(): Set<String> = store.serviceInterfaces()
}

private class ServiceLogBuffer(
    private val serviceInterface: String,
    capacity: Int,
) {
    private val sequence = AtomicLong(0L)
    private val entries: AtomicArray<LogTailEntry?> = atomicArrayOfNulls(capacity)
    private val latest = MutableSharedFlow<LogTailEntry>(
        replay = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun append(
        timestampEpochMilliseconds: Long,
        tag: LogTag,
        severity: LogTailSeverity,
        message: String,
        throwable: String?,
    ) {
        val nextSequence = sequence.addAndFetch(1L)
        val entry = LogTailEntry(
            sequence = nextSequence,
            timestampEpochMilliseconds = timestampEpochMilliseconds,
            serviceInterface = serviceInterface,
            serviceClassName = tag.serviceClassName,
            path = tag.path,
            severity = severity,
            message = message,
            throwable = throwable,
            traceId = tag.traceId,
            spanId = tag.spanId,
            traceFlags = tag.traceFlags,
        )
        val index = ((nextSequence - 1L) % entries.size).toInt()
        entries.updateAt(index) { current ->
            if (current == null || current.sequence < nextSequence) entry else current
        }
        if (entries.loadAt(index)?.sequence == nextSequence) latest.tryEmit(entry)
    }

    fun latest(): Flow<LogTailEntry> = latest.asSharedFlow()

    fun snapshot(): List<LogTailEntry> = buildList {
        repeat(entries.size) { index ->
            entries.loadAt(index)?.let(::add)
        }
    }.sortedBy(LogTailEntry::sequence)
}
