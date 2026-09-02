package ifx.logging

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.currentCoroutineContext

private val logConfig = loggerConfigInit(
    DecodingLogWriter(CommonWriter()),
    InstalledLogWriters,
)

@OptIn(ExperimentalAtomicApi::class)
private object InstalledLogWriters : LogWriter() {
    private val writers = AtomicReference<List<LogWriter>>(emptyList())

    fun install(writer: LogWriter) {
        while (true) {
            val current = writers.load()
            if (current.any { it === writer }) return
            if (writers.compareAndSet(current, current + writer)) return
        }
    }

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        writers.load().any { it.isLoggable(tag, severity) }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        writers.load().forEach { writer ->
            if (writer.isLoggable(tag, severity)) writer.log(severity, message, tag, throwable)
        }
    }
}

/** Installs an additional process-wide writer without replacing existing service loggers. */
fun installLogWriter(writer: LogWriter) = InstalledLogWriters.install(writer)

open class Log internal constructor(
    private val structuredTag: LogTag?,
    open val tag: String,
) {
    constructor(tag: String = "") : this(null, tag)
    constructor(tag: LogTag) : this(tag, tag.displayTag())

    fun withTag(tag: String): Log = structuredTag
        ?.let { Log(it.copy(path = listOf(tag))) }
        ?: Log(tag)

    fun withCorrelation(correlation: LogCorrelation?): Log {
        if (correlation == null) return this
        val tag = structuredTag ?: LogTag(path = listOf(this.tag).filter(String::isNotBlank))
        return Log(
            tag.copy(
                traceId = correlation.traceId,
                spanId = correlation.spanId,
                traceFlags = correlation.traceFlags,
            ),
        )
    }

    private val encodedTag = structuredTag?.let(LogTagCodec::encode) ?: tag
    private val delegate = Logger(logConfig, encodedTag)

    private fun encodeTag(
        tag: String,
        serviceScope: ServiceLogScope? = null,
        correlation: LogCorrelation? = null,
    ): String {
        val baseTag = when {
            structuredTag == null && serviceScope == null && correlation == null -> return tag
            structuredTag == null -> LogTag(path = listOf(tag).filter(String::isNotBlank))
            tag == this.tag -> structuredTag
            else -> structuredTag.copy(path = listOf(tag))
        }
        val scopedTag = serviceScope?.let {
            baseTag.copy(
                serviceInterface = it.serviceInterface,
                serviceClassName = it.serviceClassName,
            )
        } ?: baseTag
        if (correlation == null) return LogTagCodec.encode(scopedTag)
        return LogTagCodec.encode(
            scopedTag.copy(
                traceId = scopedTag.traceId ?: correlation.traceId,
                spanId = scopedTag.spanId ?: correlation.spanId,
                traceFlags = scopedTag.traceFlags ?: correlation.traceFlags,
            ),
        )
    }

    private suspend fun contextualTag(tag: String): String {
        val context = currentCoroutineContext()
        return encodeTag(tag, context[ServiceLogScope], context[LogCorrelation])
    }

    suspend fun trace(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.v(throwable, contextualTag(tag)) { message().toString() }

    suspend fun debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.d(throwable, contextualTag(tag)) { message().toString() }

    suspend fun info(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.i(throwable, contextualTag(tag)) { message().toString() }

    suspend fun warn(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.w(throwable, contextualTag(tag)) { message().toString() }

    suspend fun error(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.e(throwable, contextualTag(tag)) { message().toString() }

    /** Escape hatch for third-party logging interfaces that cannot suspend. */
    val synchronous = Synchronous()

    inner class Synchronous internal constructor() {
        fun trace(throwable: Throwable? = null, tag: String = this@Log.tag, message: () -> Any?) =
            delegate.v(throwable, encodeTag(tag)) { message().toString() }

        fun debug(throwable: Throwable? = null, tag: String = this@Log.tag, message: () -> Any?) =
            delegate.d(throwable, encodeTag(tag)) { message().toString() }

        fun info(throwable: Throwable? = null, tag: String = this@Log.tag, message: () -> Any?) =
            delegate.i(throwable, encodeTag(tag)) { message().toString() }

        fun warn(throwable: Throwable? = null, tag: String = this@Log.tag, message: () -> Any?) =
            delegate.w(throwable, encodeTag(tag)) { message().toString() }

        fun error(throwable: Throwable? = null, tag: String = this@Log.tag, message: () -> Any?) =
            delegate.e(throwable, encodeTag(tag)) { message().toString() }
    }

    companion object : Log("") {
        override val tag: String
            get() = Logger.tag

        fun setTag(tag: String) {
            Logger.setTag(tag)
        }
    }
}
