package ifx.host

import ifx.proxy.MethodDescriptors
import ifx.proxy.validatedMethods
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend

class GrpcServer<T : Any>(
    private val contract: KClass<T>,
    private val instance: Any,
    context: CoroutineContext = EmptyCoroutineContext
) : AbstractCoroutineServerImpl(context) {
    init {
        require(contract.isInstance(instance)) { "Service must implement given contract" }
    }

    override fun bindService(): ServerServiceDefinition {
        val serviceName =
            contract.qualifiedName ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        val ssd = ServerServiceDefinition.builder(serviceName)

        contract.validatedMethods().forEach { method ->
            val descriptor = MethodDescriptors.methodDescriptor(method, serviceName) as MethodDescriptor<Any, Any>
            val handler = ServerCalls.unaryServerMethodDefinition(context, descriptor) { req: Any ->
                method.callSuspend(instance, req) as Any
            }
            ssd.addMethod(handler)
        }
        return ssd.build()
    }

    companion object {
        inline operator fun <reified T : Any> invoke(instance: T, context: CoroutineContext = EmptyCoroutineContext) =
            GrpcServer(T::class, instance, context)

    }

}
