package ifx.host

import ifx.protocol.contract.Message
import ifx.service.IService
import kotlinx.serialization.StringFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
        .associateBy { it.toRoute() }
}

fun KFunction<*>.toRoute(): String {
    require(typeParameters.isEmpty()) { "Type parameters are not supported" }
    require(valueParameters.size <= 1) { "Only single parameter methods are supported" }
    val paramType = valueParameters.singleOrNull()?.type?.simpleName() ?: ""
    val finalName = "$name(${paramType}):${this.returnType.simpleName()}"
    return finalName
}

fun KType.simpleName() = this.toString().substringBefore("<").substringAfterLast(".")


val format: StringFormat = Json { encodeDefaults = true }


inline fun <reified T : Any> T.toMessage() = Message(header = "", body = format.encodeToString<T>(this))
fun <T : Any> T?.toMessage(type: KType) = Message(header = "", body = format.encodeToString(serializer(type), this))

fun Message.asArgFor(function: KFunction<*>): Any? {
    val paramType = function.valueParameters.singleOrNull()?.type ?: return null
    return format.decodeFromString(serializer(paramType), body)
}
