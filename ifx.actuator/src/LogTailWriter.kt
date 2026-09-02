package ifx.actuator

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import ifx.logging.LogTag
import ifx.logging.LogTagCodec

class LogTailWriter internal constructor(
    private val append: (LogTag, Severity, String, Throwable?) -> Unit,
) : LogWriter() {
    constructor(store: LogTailStore) : this(store::append)

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        LogTagCodec.decodeOrNull(tag)?.let { it.retained && it.serviceInterface != null } == true

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val structuredTag = LogTagCodec.decodeOrNull(tag) ?: return
        if (!structuredTag.retained || structuredTag.serviceInterface == null) return
        append(structuredTag, severity, message, throwable)
    }
}
