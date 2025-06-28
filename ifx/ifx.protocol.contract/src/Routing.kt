package ifx.protocol.contract

import ifx.service.IService
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

fun <T : IService> methodsFor(cls: KClass<T>): Map<String, KFunction<*>> {
    require(cls.java.isInterface) { "Must be an interface" }
    return cls.memberFunctions
        .filter {
            val declaring = it.javaMethod?.declaringClass ?: return@filter false
            IService::class.java.isAssignableFrom(declaring)
        }
        .associateBy { it.toOperation() }
}

fun KFunction<*>.toOperation(): String {
    require(typeParameters.isEmpty()) { "Type parameters are not supported" }
    require(valueParameters.size <= 1) { "Only single parameter methods are supported" }
    val paramType = valueParameters.singleOrNull()?.type?.simpleName() ?: ""
    val finalName = "$name(${paramType}):${this.returnType.simpleName()}"
    return finalName
}

fun Method.argType (): KType? = this.kotlinFunction!!.argType()
fun KFunction<*>.argType(): KType? {
    require(valueParameters.size <= 1) { "Only single parameter methods are supported" }
    return valueParameters.singleOrNull()?.type
}


fun KType.simpleName() = this.toString().substringBefore("<").substringAfterLast(".")

val format: StringFormat = Json { encodeDefaults = true }
fun <T : Any> T?.encodeToMessage(type: KType?) = Message(
    header = "",
    body = type?.let { type -> format.encodeToString(serializer(type), this) } ?: ""
)

fun Message.decodeToType(type: KType): Any? = format.decodeFromString(serializer(type), body)
fun KFunction<*>.flowType(): KType = returnType.arguments.single().type!!
