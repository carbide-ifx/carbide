package ifx.logging

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit

private val logConfig = loggerConfigInit(
    DecodingLogWriter(CommonWriter()),
    ActuatorLogWriter(ActuatorLogs::append),
)

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

    fun trace(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.v(throwable, encodeTag(tag)) { message().toString() }

    fun debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.d(throwable, encodeTag(tag)) { message().toString() }

    fun info(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.i(throwable, encodeTag(tag)) { message().toString() }

    fun warn(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.w(throwable, encodeTag(tag)) { message().toString() }

    fun error(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.e(throwable, encodeTag(tag)) { message().toString() }

    companion object : Log("") {
        override val tag: String
            get() = Logger.tag

        fun setTag(tag: String) {
            Logger.setTag(tag)
        }
    }
}
