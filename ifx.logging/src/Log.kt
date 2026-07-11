package ifx.logging

import co.touchlab.kermit.Logger
open class Log(open val tag: String = "") {
    fun withTag(tag: String): Log = Log(tag)
    private val delegate: Logger = Logger.withTag(tag)
    fun trace(throwable: Throwable? = null, tag: String = this.tag, message: () -> String) = delegate.v(throwable, tag, message)
    fun debug(throwable: Throwable? = null, tag: String = this.tag, message: () -> String) = delegate.d(throwable, tag, message)
    fun info(throwable: Throwable? = null, tag: String = this.tag, message: () -> String) = delegate.i(throwable, tag, message)
    fun warn(throwable: Throwable? = null, tag: String = this.tag, message: () -> String) = delegate.w(throwable, tag, message)
    fun error(throwable: Throwable? = null, tag: String = this.tag, message: () -> String) = delegate.e(throwable, tag, message)

    companion object : Log("") {
        override val tag: String
            get() = Logger.tag

        fun setTag(tag: String) {
            Logger.setTag(tag)
        }
    }
}
