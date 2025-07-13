package ifx.logging

import kotlin.reflect.KClass

object Log : ILogger {
    /**
     * Creates a logger with the name of the class where this function is called.
     */
    operator fun invoke(func: () -> Unit): ILogger = logger(func)
    operator fun invoke(instance: Any): ILogger = logger(instance)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun trace(message: () -> Any?): Unit = logger(message).trace(message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun debug(message: () -> Any?): Unit = logger(message).debug(message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun info(message: () -> Any?): Unit = logger(message).info(message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun warn(message: () -> Any?): Unit = logger(message).warn(message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun error(message: () -> Any?): Unit = logger(message).error(message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun trace(throwable: Throwable?, message: () -> Any?): Unit = logger(message).trace(throwable, message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun debug(throwable: Throwable?, message: () -> Any?): Unit = logger(message).debug(throwable, message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun info(throwable: Throwable?, message: () -> Any?): Unit = logger(message).info(throwable, message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun warn(throwable: Throwable?, message: () -> Any?): Unit = logger(message).warn(throwable, message)

    /**
     * Creates a logger and logs the message
     * Caution: Don't use this in a hot path, as creating a logger incurs some overhead.
     */
    override fun error(throwable: Throwable?, message: () -> Any?): Unit = logger(message).error(throwable, message)


    private fun logger(func: () -> Unit): ILogger = NamedLogger(name(func::class))

    private fun logger(instance: Any): ILogger = NamedLogger(name(instance::class))

    private fun name(clazz: KClass<*>): String = clazz.java.name.toCleanClassName()

    private fun String.toCleanClassName(): String {
        val classNameEndings = listOf("Kt$", "$")
        classNameEndings.forEach { ending ->
            val indexOfEnding = this.indexOf(ending)
            if (indexOfEnding != -1) {
                return this.substring(0, indexOfEnding)
            }
        }
        return this
    }
}


