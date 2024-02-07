package arve.ifx

import ifx.Naming
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.stub.ClientCalls
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass


class ProxyFactory(port: Int) {
    val chan: Channel = Naming.defautlChannel(port)

    inline fun <reified T : Any> create(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf<Class<*>>(T::class.java),
        GrpcHandler(chan, T::class)
    ) as T

    /**
     * Invocation handler for GRPC services.
     * Does not support suspend functions at the moment.
     */
    class GrpcHandler(private val chan: Channel, cls: KClass<*>) : InvocationHandler {
        private val serviceDescriptor = MethodDescriptors.createClientDefinition(cls)
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? = try {
            val descriptor = serviceDescriptor.methods.single { it.bareMethodName == method?.name }
            val clientCall = chan.newCall(descriptor, CallOptions.DEFAULT)
            val arg: Any = args?.first() ?: throw IllegalArgumentException("No arguments provided")
            val blockingUnaryCall = ClientCalls::class.java.getDeclaredMethod(
                "blockingUnaryCall",
                ClientCall::class.java,
                Object::class.java
            )
            blockingUnaryCall.invoke(null, clientCall, arg)
        } catch (e: InvocationTargetException) {
            throw Exception(e.targetException)
        }
    }
}

