package ifx.protocol.contract

class ProtocolException(override val cause: Throwable, val msg: String = "") : RuntimeException(msg, cause) {
    constructor(cause: Throwable, msg: () -> String) : this(cause, msg())
}
