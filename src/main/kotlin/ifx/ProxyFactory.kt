package arve.ifx

import ctx.Context
import io.grpc.*
import io.grpc.kotlin.ClientCalls
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import naming.Naming
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import io.grpc.Metadata as GrpcMetadata


class ProxyFactory(val port: Int) {
    inline fun <reified T : Any> create(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf<Class<*>>(T::class.java),
            GrpcHandler(defautlChannel(port), T::class)
        ) as T
    }

    fun defautlChannel(port: Int): ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
}

/**
 * Invocation handler for GRPC services.
 */
class GrpcHandler(private val chan: ManagedChannel, cls: KClass<*>) : InvocationHandler {
    // TODO investigate call options, especially credentials
    private val serviceDescriptor = MethodDescriptors.createServiceDescriptor(cls)

    override fun invoke(proxy: Any?, method: Method?, callArgs: Array<out Any>?): Any? = try {
        require(method != null) { "Call method must not be null" }
        require(callArgs != null) { "Call arguments must not be null" }
        val descriptor = serviceDescriptor.methodDescriptorFor(method)
        val (arg, originalContinuation) = extractArg(callArgs)
        runBlocking(originalContinuation?.context ?: EmptyCoroutineContext){
            ClientCalls.unaryRpc(chan, descriptor, arg, CallOptions.DEFAULT, getHeaders())
        }
    } catch (se: Exception) {
        throw WrappedTestException(se)
    }

    private fun extractArg(callArgs: Array<out Any>): Pair<Any, Continuation<Any?>?> {
        val isSingleParam = callArgs.size == 1
        val isSingleSuspendParam = callArgs.size == 2 && callArgs.last() is Continuation<*>
        return when {
            isSingleParam -> callArgs.first() to null
            isSingleSuspendParam -> callArgs.first() to callArgs.last() as Continuation<Any?>
            else -> throw IllegalArgumentException("Method must have exactly one parameter or one parameter and a continuation")
        }
    }

    private suspend fun getHeaders(): GrpcMetadata {
        val context = currentCoroutineContext()[Context]
        val key = Context.CUSTOM_HEADER_KEY
        val meta = GrpcMetadata()
        meta.put(key, context ?: Context.EMPTY)
        return meta
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun ServiceDescriptor.methodDescriptorFor(method: Method): MethodDescriptor<Any, Any> =
            methods.singleOrNull {
                it.fullMethodName == Naming.generateFullMethodName(name, method)
            } as MethodDescriptor<Any, Any>
    }
}

class WrappedTestException(cause: Throwable) : RuntimeException(cause)
