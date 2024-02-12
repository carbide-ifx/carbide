package arve.ifx

import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import kotlinx.serialization.serializer
import naming.Naming
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType

object MethodDescriptors {
    fun createServiceDescriptor(cls: KClass<*>): ServiceDescriptor {
        val serviceName = cls.qualifiedName
            ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        return ServiceDescriptor(serviceName, listMethods(cls).map { methodDescriptor(it, serviceName) })
    }


    fun methodDescriptor(method: KFunction<*>, serviceName: String): MethodDescriptor<*, *> {
        val paramType = method.valueParameters.single().type.javaType
        val returnType = method.returnType.javaType
        return MethodDescriptor
            .newBuilder(MarshallerFactoryDynamic.json(paramType), MarshallerFactoryDynamic.json(returnType))
            .setFullMethodName(Naming.generateFullMethodName(serviceName, method))
            .setType(MethodDescriptor.MethodType.UNARY).setSampledToLocalTracing(true).build()
    }




    fun listMethods(cls: KClass<*>): Collection<KFunction<*>> {
        require(cls.java.isInterface) { "Contract ${cls.simpleName} must be an interface" }
        val members = cls.declaredFunctions
        members.forEach { method ->
            val parameter = method.valueParameters.singleOrNull()
                ?: throw IllegalArgumentException("Method ${cls.simpleName}#${method.name}() must have exactly one parameter")
            require(method.returnType.isSerializable()) { "Return type of ${cls.simpleName}#${method.name} (`${method.returnType}`) must be @Serializable" }
            require(parameter.type.isSerializable()) { "Parameter of ${cls.simpleName}#${method.name} (`${parameter.type}`) must be @Serializable" }

        }
        return members
    }

    private fun KType.isSerializable(): Boolean = runCatching { serializer(this) }.isSuccess
}

