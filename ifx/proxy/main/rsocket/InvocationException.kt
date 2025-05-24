package ifx.proxy.rsocket

class InvocationException(val wrappedError: Throwable) : RuntimeException()
