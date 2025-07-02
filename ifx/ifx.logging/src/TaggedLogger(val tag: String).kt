package ifx.logging

import io.github.oshai.kotlinlogging.KotlinLogging

class NamedLogger(name: String): ILogger {
    private val klogger = KotlinLogging.logger(name)

    override fun trace(message: () -> Any?) = klogger.trace(message)
    override fun debug(message: () -> Any?) = klogger.debug(message)
    override fun info(message: () -> Any?) = klogger.info(message)
    override fun warn(message: () -> Any?) = klogger.warn(message)
    override fun error(message: () -> Any?) = klogger.error(message)

    override fun trace(throwable: Throwable?, message: () -> Any?) = klogger.trace(throwable, message)
    override fun debug(throwable: Throwable?, message: () -> Any?) = klogger.debug(throwable, message)
    override fun info(throwable: Throwable?, message: () -> Any?) = klogger.info(throwable, message)
    override fun warn(throwable: Throwable?, message: () -> Any?) = klogger.warn(throwable, message)
    override fun error(throwable: Throwable?, message: () -> Any?) = klogger.error(throwable, message)
}
