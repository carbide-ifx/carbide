package host

import arve.ifx.MethodDescriptors
import io.grpc.MethodDescriptor
import io.grpc.ServerMethodDefinition
import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class GrpcServer<T : Any>(
    private val type: KClass<T>,
    private val instance: Any,
    context: CoroutineContext = EmptyCoroutineContext
) : AbstractCoroutineServerImpl(context) {
    init {
        require(type.isInstance(instance)) { "Service must implement given contract" }
    }

    override fun bindService(): ServerServiceDefinition {
        val serviceName =
            type.qualifiedName ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
//        val ssd = ServerServiceDefinition.builder(MethodDescriptors.createServiceDescriptor(cls))
        val ssd = ServerServiceDefinition.builder(serviceName)

        MethodDescriptors.listMethods(type).forEach { method ->
            val descriptor: MethodDescriptor<*, *> = MethodDescriptors.methodDescriptor(method, serviceName)
            val handler: ServerMethodDefinition<*, *> = createHandler(method, descriptor)
            ssd.addMethod(handler)
        }
        return ssd.build()
    }

    companion object {
        inline operator fun <reified T : Any> invoke(instance: T, context: CoroutineContext = EmptyCoroutineContext) =
            GrpcServer(T::class, instance, context)
    }

    private fun createHandler(method: KFunction<*>, descriptor: MethodDescriptor<*, *>): ServerMethodDefinition<*, *> =
        ServerCalls.unaryServerMethodDefinition(context, descriptor as MethodDescriptor<Any, Any>) { req: Any ->
            method.callSuspend(instance, req) as Any
        }
}
