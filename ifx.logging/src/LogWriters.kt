package ifx.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/** Presents structured IFX tags as ordinary human-readable tags to a standard writer. */
class DecodingLogWriter(
    private val delegate: LogWriter,
) : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean =
        delegate.isLoggable(tag.displayTag(), severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) =
        delegate.log(severity, message, tag.displayTag(), throwable)

    private fun String.displayTag(): String = LogTagCodec.decodeOrNull(this)?.displayTag() ?: this
}

class ActuatorLogWriter internal constructor(
    private val append: (LogTag, Severity, String, Throwable?) -> Unit,
) : LogWriter() {
    constructor(store: ActuatorLogStore) : this(store::append)

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        LogTagCodec.decodeOrNull(tag)?.serviceInterface != null

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val structuredTag = LogTagCodec.decodeOrNull(tag) ?: return
        if (structuredTag.serviceInterface == null) return
        append(structuredTag, severity, message, throwable)
    }
}
