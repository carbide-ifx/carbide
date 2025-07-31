package ifx.protocol.contract

class ProtocolException(override val message: String? = null, override val cause: Throwable?) : RuntimeException(message, cause) {
    constructor(cause: Throwable, msg: () -> String) : this(msg(), cause)
}
