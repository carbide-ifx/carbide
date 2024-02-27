package arve.ifx

import io.grpc.MethodDescriptor
import io.grpc.ServiceDescriptor
import naming.Naming
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType
import arve.host.GrpcServer.Companion.methods

object MethodDescriptors {
    fun createServiceDescriptor(cls: KClass<*>): ServiceDescriptor {
        val serviceName = cls.qualifiedName
            ?: throw IllegalArgumentException("Could not retrieve contract qualified name")
        return ServiceDescriptor(serviceName, cls.methods().map { methodDescriptor(it, serviceName) })
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

