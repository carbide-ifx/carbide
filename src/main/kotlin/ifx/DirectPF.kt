package arve.ifx

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import naming.Naming
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException


class DirectProxyFactory() {
    inline fun <reified T : Any> create(instance: T): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf<Class<*>>(T::class.java),
            CorrectExceptionLogger(instance)
        ) as T
    }

    fun defautlChannel(port: Int): ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
}

/**
 * Invocation handler for GRPC services.
 * Does not support suspend functions at the moment.
 */

class CorrectExceptionLogger<T>(private val instance: T) : InvocationHandler {

    override fun invoke(proxy: Any?, method: Method?, callArgs: Array<out Any>?): Any? {
        try {
            require(method != null) { "Call method must not be null" }
            require(callArgs != null) { "Call arguments must not be null" }
//            val descriptor = serviceDescriptor.methodDescriptorFor(method)
            val lastArg = callArgs.lastOrNull()
            val (arg, originalContinuation) = extractArg(callArgs)
            return if (originalContinuation == null) {
                // not a suspend func, just invoke regularly
                 method.invoke(instance, *callArgs)
            } else {

                // Step 1: Wrap the underlying continuation to intercept exceptions.
                @Suppress("UNCHECKED_CAST")
                val wrappedContinuation = object : Continuation<Any?> {
                    override val context: CoroutineContext get() = originalContinuation.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.exceptionOrNull()?.let { err ->
                            // Step 2: log intercepted exception and resume with our custom wrapped exception.
                            println("Correctly caught underlying coroutine exception $err")
                            originalContinuation.resumeWithException(WrappedTestException(err))
                        } ?: originalContinuation.resumeWith(result)
                    }
                }
                // Step 3: launch the suspend function with our wrapped continuation using the underlying scope and context, but force it to run in the IO thread pool
                CoroutineScope(originalContinuation.context).launch(Dispatchers.IO + originalContinuation.context) {
                    val newArgs = arrayOf(arg, wrappedContinuation)
                    val res = method.invoke(instance, *newArgs)
                    res
                }
                kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
        } catch (e: InvocationTargetException) {
            e.targetException?.let { targetException ->
                println("Correctly caught underlying exception $targetException")
                throw WrappedTestException(targetException)
            } ?: throw WrappedTestException(e)
        }
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

    companion object {
        private fun ServiceDescriptor.methodDescriptorFor(method: Method): MethodDescriptor<Any, Any> =
            methods.singleOrNull {
                it.fullMethodName == Naming.generateFullMethodName(name, method)
            } as MethodDescriptor<Any, Any>
    }
}
