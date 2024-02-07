package arve.ifx

import io.grpc.MethodDescriptor
import io.grpc.ServerCallHandler
import io.grpc.ServerServiceDefinition
import io.grpc.ServiceDescriptor
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import kotlinx.serialization.Serializable
import naming.Naming
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType

object MethodDescriptors {
    fun createClientDefinition(cls: KClass<*>): ServiceDescriptor {
        val serviceName = cls.qualifiedName
            ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        return ServiceDescriptor(serviceName, listMethods(cls).map { methodDescriptor(it, serviceName) })
    }

    fun <T : Any> createServiceDefinition(instance: T, cls: KClass<out T>): ServerServiceDefinition {
        val serviceName =
            cls.qualifiedName ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        val ssd = ServerServiceDefinition.builder(serviceName)

        listMethods(cls).forEach { method ->
            val descriptor: MethodDescriptor<*, *> = methodDescriptor(method, serviceName)
            val handler: ServerCallHandler<*, *> = createHandler(method, instance)
            val addMethod = ssd::class.declaredFunctions.single { it.name == "addMethod" && it.parameters.size == 3 }
            addMethod.call(ssd, descriptor, handler)
        }
        return ssd.build()
    }

    private fun createHandler(method: KFunction<*>, instance: Any): ServerCallHandler<*, *> {
        val args = { request: Any, responseObserver: StreamObserver<Any> ->
            responseObserver.onNext(method.call(instance, request))
            responseObserver.onCompleted()
        }
        return ServerCalls.asyncUnaryCall(args)
    }

    private fun methodDescriptor(method: KFunction<*>, serviceName: String): MethodDescriptor<*, *> {
        val paramType = method.valueParameters.single().type.javaType
        val returnType = method.returnType.javaType
        return MethodDescriptor
            .newBuilder(MarshallerFactoryDynamic.json(paramType), MarshallerFactoryDynamic.json(returnType))
            .setFullMethodName(Naming.generateFullMethodName(serviceName, method))
            .setType(MethodDescriptor.MethodType.UNARY).setSampledToLocalTracing(true).build()
    }




    private fun listMethods(cls: KClass<*>): Collection<KFunction<*>> {
        require(cls.java.isInterface) { "Contract must be an interface" }
        val members = cls.declaredFunctions
        members.forEach { method ->
            val parameter = method.valueParameters.singleOrNull()
                ?: throw IllegalArgumentException("Method ${cls.simpleName}#${method.name}() must have exactly one parameter")
            require(method.returnType.isSerializable()) { "Return type of ${cls.simpleName}#${method.name} (`${method.returnType}`) must be @Serializable" }
            require(parameter.type.isSerializable()) { "Parameter of ${cls.simpleName}#${method.name} (`${parameter.type}`) must be @Serializable" }

        }
        return members
    }

    private fun KType.isSerializable(): Boolean {
        return if (arguments.isNotEmpty()) {
            arguments.all { it.type?.isSerializable() ?: false }
        } else ClassLoader.getSystemClassLoader().loadClass(this.javaType.typeName)
            .isAnnotationPresent(Serializable::class.java)
    }
}

