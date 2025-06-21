package ifx.protocol.rsocket

import ifx.service.IService
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

fun <T : IService> methodsFor(cls: KClass<T>): Map<String, KFunction<*>> {
    require(cls.java.isInterface) { "Must be an interface" }
    return cls.memberFunctions
        .filter {
            val declaring = it.javaMethod?.declaringClass ?: return@filter false
            IService::class.java.isAssignableFrom(declaring)
        }
        .associateBy { it.operationName() }
}


fun KFunction<*>.operationName(): String {
    require(typeParameters.isEmpty()) { "Type parameters are not supported" }
    require(valueParameters.size <= 1) { "Only single parameter methods are supported" }
    val paramType = valueParameters.singleOrNull()?.type?.simpleName() ?: ""
    val finalName =  "$name(${paramType}):${this.returnType.simpleName()}"
    return finalName
}

private fun KType.simpleName() = this.toString().substringBefore("<").substringAfterLast(".")
