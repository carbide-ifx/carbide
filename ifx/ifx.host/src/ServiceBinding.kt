package ifx.host

import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend


class ServiceBinding<out T : IService>(
    contract: KClass<out T>,
    private val instance: T,
) : IMessageHandler {
    val methods = methodsFor(contract)

    override suspend fun fireAndForget(operation: String, message: Message) {
        val method = methods[operation] ?: throw IllegalArgumentException("No method found for address: $operation")
        try {
            method.invoke(instance, message)
        } catch (exception: Throwable) {
            throw ProtocolException(exception) { "Failed to invoke method ${operation}: ${exception.message}" }
        }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        val method = methods[operation] ?: throw IllegalArgumentException("No method found for address: $operation")
        val result = method.invoke(instance, message)
        return result.toMessage(method.returnType)
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        val method = methods[operation] ?: throw IllegalArgumentException("No method found for address: $operation")
        val result = method.invoke(instance, message) as Flow<*>
        return result.map { it.toMessage(method.flowType()) }
    }
}

private suspend fun <R> KFunction<R>.invoke(instance: Any, payload: Message): R {
    val args = listOfNotNull(instance, payload.asArgFor(this)).toTypedArray()
    return callSuspend(*args)
}

private fun KFunction<*>.flowType(): KType = returnType.arguments.single().type!!
