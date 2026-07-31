package ifx.logging

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit

private val logConfig = loggerConfigInit(CommonWriter())

open class Log(open val tag: String = "") {
    fun withTag(tag: String): Log = Log(tag)
    private val delegate = Logger(logConfig, tag)


    fun trace(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.v(throwable, tag) { message().toString() }

    fun debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.d(throwable, tag) { message().toString() }

    fun info(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.i(throwable, tag) { message().toString() }

    fun warn(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.w(throwable, tag) { message().toString() }

    fun error(throwable: Throwable? = null, tag: String = this.tag, message: () -> Any) =
        delegate.e(throwable, tag) { message().toString() }


    companion object : Log("") {
        override val tag: String
            get() = Logger.tag

        fun setTag(tag: String) {
            Logger.setTag(tag)
        }
    }
}
