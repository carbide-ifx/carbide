package ifx.logging

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

    private val encodedTag = structuredTag?.let(LogTagCodec::encode) ?: tag
    private val delegate = Logger(logConfig, encodedTag)

    private fun encodeTag(tag: String): String = when {
        structuredTag == null -> tag
        tag == this.tag -> encodedTag
        else -> LogTagCodec.encode(structuredTag.copy(path = listOf(tag)))
    }

    fun trace(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.v(throwable, encodeTag(tag)) { message().toString() }

    fun debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.d(throwable, encodeTag(tag)) { message().toString() }

    fun info(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.i(throwable, encodeTag(tag)) { message().toString() }

    fun warn(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.w(throwable, encodeTag(tag)) { message().toString() }

    fun error(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any?) =
        delegate.e(throwable, encodeTag(tag)) { message().toString() }

    companion object : Log("") {
        override val tag: String
            get() = Logger.tag

        fun setTag(tag: String) {
            Logger.setTag(tag)
        }
    }
}
