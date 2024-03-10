package ifx.proxy

import ifx.ctx.Context
import ifx.naming.Naming
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import io.grpc.kotlin.ClientCalls
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import io.grpc.Metadata as GrpcMetadata


class ProxyFactory(val port: Int) {
    inline fun <reified T : Any> create(context: Context? = null, name: String = "default"): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf<Class<*>>(T::class.java),
        GrpcHandler(defautlChannel(port), context ?: EmptyCoroutineContext, T::class, name)
    ) as T

    fun defautlChannel(port: Int): ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
}

/**
 * Invocation handler for GRPC services.
 */
class GrpcHandler(
    private val chan: ManagedChannel,
    private val context: CoroutineContext,
    cls: KClass<*>,
    private val name: String
) : InvocationHandler {
    // TODO investigate call options, especially credentials
    private val serviceDescriptor = MethodDescriptors.createServiceDescriptor(cls)


    fun <T> Continuation<*>.invokeSuspendFunction(block: suspend () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return (block as (Continuation<*>) -> T)(this)
    }

    @SuppressWarnings("kotlin:S6311")
    override fun invoke(proxy: Any?, method: Method?, callArgs: Array<out Any>?): Any = try {
        require(method != null) { "Call method must not be null" }
        require(callArgs != null) { "Call arguments must not be null" }
        val descriptor = serviceDescriptor.methodDescriptorFor(method)
        val (arg, originalContinuation) = extractArg(callArgs)
        val originalCtx = (originalContinuation?.context ?: EmptyCoroutineContext)
        originalContinuation?.invokeSuspendFunction {
            val headers = headersFromContext(originalCtx + context)
            ClientCalls.unaryRpc(chan, descriptor, arg, CallOptions.DEFAULT, headers)
        }
            ?: runBlocking {
                val headers = headersFromThreadLocal(context)
                ClientCalls.unaryRpc(chan, descriptor, arg, CallOptions.DEFAULT, headers)
            }
    } catch (se: Exception) {
        throw WrappedTestException(se)
    }


    private fun headersFromContext(ctx: CoroutineContext): GrpcMetadata {
        val context = ctx[Context]
        val key = Context.METADATA_KEY
        val meta = GrpcMetadata()
        meta.put(key, context ?: Context())
        return meta
    }

    private fun headersFromThreadLocal(ctx: CoroutineContext): GrpcMetadata {
        val context = Context.BLOCKING_CONTEXT_KEY.get() ?: ctx[Context] ?: Context()
        val key = Context.METADATA_KEY
        val meta = GrpcMetadata()
        meta.put(key, context)
        return meta
    }


    private fun extractArg(callArgs: Array<out Any>): Pair<Any, Continuation<*>?> {
        val isSingleParam = callArgs.size == 1
        val isSingleSuspendParam = callArgs.size == 2 && callArgs.last() is Continuation<*>
        return when {
            isSingleParam -> callArgs.first() to null
            isSingleSuspendParam -> callArgs.first() to callArgs.last() as Continuation<*>
            else -> throw IllegalArgumentException("Method must have exactly one parameter or one parameter and a continuation")
        }
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


