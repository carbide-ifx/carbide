package ifx.logging

interface ILogger {
    fun trace(message: () -> Any?): Unit


    fun debug(message: () -> Any?): Unit


    fun info(message: () -> Any?): Unit


    fun warn(message: () -> Any?): Unit


    fun error(message: () -> Any?): Unit

    fun trace(throwable: Throwable?, message: () -> Any?)

    fun debug(throwable: Throwable?, message: () -> Any?)

    fun info(throwable: Throwable?, message: () -> Any?)

    fun warn(throwable: Throwable?, message: () -> Any?)

    fun error(throwable: Throwable?, message: () -> Any?)

}
