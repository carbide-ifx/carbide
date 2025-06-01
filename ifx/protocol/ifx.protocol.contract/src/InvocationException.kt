package ifx.protocol.contract

class InvocationException(val wrappedError: Throwable) : RuntimeException()
