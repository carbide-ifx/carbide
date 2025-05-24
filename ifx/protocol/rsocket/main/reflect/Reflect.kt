package ifx.rsocket.reflect

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.serializer
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod


enum class MethodKind { FireAndForget, RequestResponse, RequestStream, }
fun KFunction<*>.classify(): MethodKind = when (this.returnType.classifier) {
    Unit::class -> MethodKind.FireAndForget
    Flow::class -> MethodKind.RequestStream
    else -> MethodKind.RequestResponse
}


private fun KType.isSerializable(): Boolean = runCatching { serializer(this) }.isSuccess


fun KFunction<*>.validate(className: String?) {
    val parameter = valueParameters.singleOrNull()
        ?: throw IllegalArgumentException("Method $className#${name}() must have exactly one parameter")
    require(returnType.isSerializable()) { "Return type of $className#${name} (`${returnType}`) must be @Serializable" }
    require(parameter.type.isSerializable()) { "Parameter of $className#${name} (`${parameter.type}`) must be @Serializable" }
}


fun KFunction<*>.route(): String {
    val method = this.javaMethod!!
    val isSingleParam = method.parameterTypes.size == 1
    val isSingleSuspendParam = method.parameterTypes.size == 2 && method.parameterTypes[1] == Continuation::class.java
    require(isSingleParam || isSingleSuspendParam) { "Method must have exactly one parameter or one parameter and a continuation" }
    val paramType = method.parameterTypes.first().typeName
    return "${method.name}#$paramType"
}

//
//
//private fun extractArg(callArgs: Array<out Any>): Pair<Any, Continuation<Any?>?> {
//    val isSingleParam = callArgs.size == 1
//    val isSingleSuspendParam = callArgs.size == 2 && callArgs.last() is Continuation<*>
//    return when {
//        isSingleParam -> callArgs.first() to null
//        isSingleSuspendParam -> callArgs.first() to callArgs.last() as Continuation<Any?>
//        else -> throw IllegalArgumentException("Method must have exactly one parameter or one parameter and a continuation")
//    }
//}


