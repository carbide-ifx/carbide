package ifx.host

import ifx.logging.Log
import io.ktor.util.logging.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

internal actual fun kermitKtorLogger(tag: String): Logger = KermitSlf4jLogger(tag)

private class KermitSlf4jLogger(tag: String) : LegacyAbstractLogger() {
    private val delegate = Log(tag)

    init {
        name = tag
    }

    override fun isTraceEnabled(): Boolean = false

    override fun isDebugEnabled(): Boolean = false

    override fun isInfoEnabled(): Boolean = true

    override fun isWarnEnabled(): Boolean = true

    override fun isErrorEnabled(): Boolean = true

    override fun getFullyQualifiedCallerName(): String = KermitSlf4jLogger::class.java.name

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        val message = MessageFormatter.basicArrayFormat(messagePattern ?: "null", arguments)
        when (level) {
            Level.ERROR -> delegate.error(throwable) { message }
            Level.WARN -> delegate.warn(throwable) { message }
            Level.INFO -> delegate.info(throwable) { message }
            Level.DEBUG -> delegate.debug(throwable) { message }
            Level.TRACE -> delegate.trace(throwable) { message }
        }
    }
}
