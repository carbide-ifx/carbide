package ifx.contract

class InvocationException(val wrappedError: Throwable) : RuntimeException()
