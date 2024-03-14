package ifx.proxy

import ifx.context.Context
import ifx.naming.Naming
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import io.grpc.kotlin.ClientCalls
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Invocation handler for GRPC services.
 */
class GrpcHandler(private val chan: ManagedChannel, private val context: CoroutineContext, private val cls: KClass<*>) :
    InvocationHandler {


    @SuppressWarnings("kotlin:S6311")
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any = try {
        val descriptor = MethodDescriptors.createServiceDescriptor(cls).methodDescriptorFor(method)
        val (arg, originalContinuation) = extractArg(args)
        originalContinuation.invokeSuspendFunction {
            val headers = headersFromContext(originalContinuation.context + context)
            ClientCalls.unaryRpc(chan, descriptor, arg, CallOptions.DEFAULT, headers)
        }
    } catch (se: Throwable) {
        throw InvocationException("yes")
    }

    private fun <T> Continuation<*>.invokeSuspendFunction(block: suspend () -> T): T {
        @Suppress("UNCHECKED_CAST") return try { (block as (Continuation<*>) -> T)(this)} catch (e: Throwable) {
            throw e
        }
    }

    private fun headersFromContext(ctx: CoroutineContext): Metadata {
        val context = ctx[Context]
        val key = Context.METADATA_KEY
        val meta = Metadata()
        meta.put(key, context ?: Context())
        return meta
    }

    private fun extractArg(callArgs: Array<out Any>): Pair<Any, Continuation<*>> =
        if (callArgs.size == 2 && callArgs.last() is Continuation<*>) {
            callArgs.first() to callArgs.last() as Continuation<*>
        } else {
            throw IllegalArgumentException("Method must have exactly one parameter or one parameter and a continuation")
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun ServiceDescriptor.methodDescriptorFor(method: Method): MethodDescriptor<Any, Any> =
            methods.singleOrNull {
                it.fullMethodName == Naming.generateFullMethodName(name, method)
            } as MethodDescriptor<Any, Any>
    }
}
