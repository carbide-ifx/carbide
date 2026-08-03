package ifx.protocol.rsocket

import ifx.logging.Log
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.logging.Logger
import io.rsocket.kotlin.logging.LoggerFactory
import io.rsocket.kotlin.logging.LoggingLevel

@OptIn(RSocketLoggingApi::class)
internal object KermitRSocketLoggerFactory : LoggerFactory {
    override fun logger(tag: String): Logger = KermitRSocketLogger(tag)
}

@OptIn(RSocketLoggingApi::class)
private class KermitRSocketLogger(
    override val tag: String,
) : Logger {
    private val delegate = Log(tag)

    override fun isLoggable(level: LoggingLevel): Boolean = level >= LoggingLevel.INFO

    override fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?) {
        if (!isLoggable(level)) return

        val text = message.toString()
        when (level) {
            LoggingLevel.ERROR -> delegate.error(throwable) { text }
            LoggingLevel.WARN -> delegate.warn(throwable) { text }
            LoggingLevel.INFO -> delegate.info(throwable) { text }
            LoggingLevel.DEBUG -> delegate.debug(throwable) { text }
            LoggingLevel.TRACE -> delegate.trace(throwable) { text }
        }
    }
}
