package ifx.protocol.contract

import ifx.context.Context
import ifx.service.IService
import kotlinx.coroutines.currentCoroutineContext
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
    require(IService::class.java.isAssignableFrom(cls.java)) { "Must be a service interface" }
    return cls.memberFunctions
        .filter {
            val declaring = it.javaMethod?.declaringClass ?: return@filter false
            declaring.isInterface
        }
        .associateBy { it.toOperation() }
}

fun KFunction<*>.toOperation(): String {
    require(typeParameters.isEmpty()) { "Type parameters are not supported" }
    require(valueParameters.size <= 1) { "Could not create binding for method ${javaMethod?.declaringClass?.simpleName}:${name}. Only single parameter methods are supported" }
    val paramType = valueParameters.singleOrNull()?.type?.simpleName() ?: ""
    val finalName = "$name(${paramType}):${this.returnType.simpleName()}"
    return finalName
}

fun Method.argType(): KType? = this.kotlinFunction!!.argType()
fun KFunction<*>.argType(): KType? {
    require(valueParameters.size <= 1) { "Only single parameter methods are supported" }
    return valueParameters.singleOrNull()?.type
}


fun KType.simpleName() = this.toString().substringBefore("<").substringAfterLast(".")

val RpcFormat = Json {
    encodeDefaults = true
    prettyPrint = false
}

suspend fun <T : Any> T?.encodeToMessage(type: KType?) = Message(
    header = RpcFormat.encodeToString(mapOf(Context.HEADER_KEY to currentCoroutineContext()[Context]).mapNotNullValues()),
    body = type?.let { type -> RpcFormat.encodeToString(serializer(type), this) } ?: ""
)

fun Message.decodeToType(type: KType): Any? = RpcFormat.decodeFromString(serializer(type), body)
fun KFunction<*>.flowType(): KType = returnType.arguments.single().type!!


private fun <K, V> Map<K, V?>.mapNotNullValues(): Map<K, V> = mapNotNull { it.value?.let { value -> it.key to value } }
    .toMap()
