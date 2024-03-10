package ifx.host

import ifx.proxy.MethodDescriptors
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import kotlinx.serialization.serializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters

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

        // Todo not GRPC specific. Find a home for this
        fun KClass<*>.validatedMethods(): Collection<KFunction<*>> {
            require(java.isInterface) { "Contract $simpleName must be an interface" }
            val members = declaredFunctions
            members.forEach { method ->
                val parameter = method.valueParameters.singleOrNull()
                    ?: throw IllegalArgumentException("Method $simpleName#${method.name}() must have exactly one parameter")
                require(method.returnType.isSerializable()) { "Return type of $simpleName#${method.name} (`${method.returnType}`) must be @Serializable" }
                require(parameter.type.isSerializable()) { "Parameter of $simpleName#${method.name} (`${parameter.type}`) must be @Serializable" }

            }
            return members
        }

        private fun KType.isSerializable(): Boolean = runCatching { serializer(this) }.isSuccess
    }

}
