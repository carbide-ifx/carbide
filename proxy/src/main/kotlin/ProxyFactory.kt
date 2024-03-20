package ifx.proxy

import ifx.context.Context
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.lang.reflect.Proxy
import kotlin.coroutines.EmptyCoroutineContext

// TODO: ToString: https://stackoverflow.com/questions/58156371/implementing-equals-hashcode-and-tostring-on-java-lang-reflect-proxy
// TODO: Exception handling before calling the server

class ProxyFactory(val port: Int) {
    inline fun <reified T : Any> create(context: Context? = null): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf<Class<*>>(T::class.java),
        GrpcClientHandler(defautlChannel(port), context ?: EmptyCoroutineContext, T::class)
    ) as T

    fun defautlChannel(port: Int): ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
}

class InvocationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

