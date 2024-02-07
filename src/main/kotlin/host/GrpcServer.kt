package host

import arve.ifx.MethodDescriptors
import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

class GrpcServer<T : Any>(
    private val type: KClass<T>,
    private val instance: Any,
    context: CoroutineContext = EmptyCoroutineContext
) : AbstractCoroutineServerImpl(context) {
    init {
        require(type.isInstance(instance)) { "Service must implement given contract" }
    }

    override fun bindService(): ServerServiceDefinition =
        MethodDescriptors.createServiceDefinition(instance, type)

    companion object {
        inline operator fun <reified T : Any> invoke(instance: T, context: CoroutineContext = EmptyCoroutineContext) =
            GrpcServer(T::class, instance, context)
    }
}
