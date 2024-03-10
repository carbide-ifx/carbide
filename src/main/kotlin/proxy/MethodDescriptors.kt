package ifx.proxy

import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import ifx.naming.Naming
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType
import ifx.host.GrpcServer.Companion.validatedMethods

object MethodDescriptors {
    fun createServiceDescriptor(cls: KClass<*>): ServiceDescriptor {
        val serviceName = cls.qualifiedName
            ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        return ServiceDescriptor(serviceName, cls.validatedMethods().map { methodDescriptor(it, serviceName) })
    }


    fun methodDescriptor(method: KFunction<*>, serviceName: String): MethodDescriptor<*, *> {
        val paramType = method.valueParameters.single().type.javaType
        val returnType = method.returnType.javaType
        return MethodDescriptor
            .newBuilder(MarshallerFactoryDynamic.json(paramType), MarshallerFactoryDynamic.json(returnType))
            .setFullMethodName(Naming.generateFullMethodName(serviceName, method))
            .setType(MethodDescriptor.MethodType.UNARY).setSampledToLocalTracing(true).build()
    }



}

