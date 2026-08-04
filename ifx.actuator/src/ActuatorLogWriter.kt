package ifx.actuator

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import ifx.logging.LogTag
import ifx.logging.LogTagCodec

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
