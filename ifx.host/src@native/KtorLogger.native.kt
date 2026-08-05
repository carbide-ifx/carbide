package ifx.host

import ifx.logging.Log
import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger

internal actual fun kermitKtorLogger(tag: String): Logger = KermitNativeKtorLogger(tag)

private class KermitNativeKtorLogger(tag: String) : Logger {
    private val delegate = Log(tag)

    override val level: LogLevel = LogLevel.INFO

    override fun error(message: String) = delegate.error { message }

    override fun error(message: String, cause: Throwable) = delegate.error(cause) { message }

    override fun warn(message: String) = delegate.warn { message }

    override fun warn(message: String, cause: Throwable) = delegate.warn(cause) { message }

    override fun info(message: String) = delegate.info { message }

    override fun info(message: String, cause: Throwable) = delegate.info(cause) { message }

    override fun debug(message: String) = Unit

    override fun debug(message: String, cause: Throwable) = Unit

    override fun trace(message: String) = Unit

    override fun trace(message: String, cause: Throwable) = Unit
}
