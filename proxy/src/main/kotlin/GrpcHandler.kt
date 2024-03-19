package ifx.proxy

import ifx.context.Context
import ifx.naming.Naming
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import io.grpc.kotlin.ClientCalls
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Invocation handler for GRPC services.
 */
class GrpcClientHandler(
    private val chan: ManagedChannel, private val context: CoroutineContext, private val cls: KClass<*>
) : InvocationHandler {

    private val log = KotlinLogging.logger { }

    @SuppressWarnings("kotlin:S6311")
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any = try {
        requireNotNull(args) { "Method must have exactly one parameter" }
        require((args.size == 2 && args.last() is Continuation<*>)) {
            "Method must have exactly one parameter and be suspending"
        }
        val descriptor = MethodDescriptors.createServiceDescriptor(cls).methodDescriptorFor(method)
        val arg = args.first()
        val originalContinuation = args.last() as Continuation<*>
        runBlocking {
            val headers = headersFromContext(originalContinuation.context + context)
            ClientCalls.unaryRpc(chan, descriptor, arg, CallOptions.DEFAULT, headers)
        }
    } catch (se: Throwable) {
        val msg = "Error invoking method: ${cls.simpleName}.${method.name} via proxy: ${se.message}"
        log.warn(se) { msg }
        throw InvocationException(msg, se)
    }


    private fun headersFromContext(ctx: CoroutineContext): Metadata {
        val context = ctx[Context]
        val key = Context.METADATA_KEY
        val meta = Metadata()
        meta.put(key, context ?: Context())
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
