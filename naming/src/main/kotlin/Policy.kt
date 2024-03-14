package ifx.proxy

import kotlinx.serialization.serializer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure


fun KClass<*>.validatedMethods(): Collection<KFunction<*>> {
    require(java.isInterface) { "Contract $simpleName must be an interface" }
    val members = declaredFunctions
    members.forEach { method ->
        val parameter = method.valueParameters.singleOrNull()
            ?: throw IllegalArgumentException("Method $simpleName#${method.name}() must have exactly one parameter")

        require(method.returnType.isSerializable()) { "Return type of $simpleName#${method.name} (`${method.returnType}`) must be @Serializable" }
        require(parameter.type.isSerializable()) { "Parameter of $simpleName#${method.name} (`${parameter.type}`) must be @Serializable" }
    }
    return members
}

private fun KType.isSerializable(): Boolean {
    val argumentTypesAreSerializable = this.arguments.map { it.type?.isSerializable() ?: true }.all { it }
    return this.isConcreteSerializable() && argumentTypesAreSerializable
}

private fun KType.isConcreteSerializable(): Boolean {
    val cls = this.jvmErasure
    val childrenAreSerializable = cls.sealedSubclasses.map { it.createType().isSerializable() }.all { it }
    val hasSerializer = runCatching {
        val ser = serializer(this)
//        println(ser.toString())
    }.isSuccess && childrenAreSerializable
//    println("${cls.simpleName} (sealed: ${cls.isSealed}) (hasSerializer: $hasSerializer)")
    return hasSerializer
}

class ContractPolicyViolation(message: String) : Exception(message)

@OptIn(ExperimentalContracts::class)
fun require(value: Boolean, lazyMessage: () -> Any) {
    contract { returns() implies value }
    if (!value) throw ContractPolicyViolation(lazyMessage().toString()) else value
}
