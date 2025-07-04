package ifx.host

import ifx.context.Context
import ifx.logging.Log
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.decodeToType
import ifx.protocol.contract.encodeToMessage
import ifx.protocol.contract.flowType
import ifx.protocol.contract.methodsFor
import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.valueParameters

class ServiceBinding<out T : IService>(
    contract: KClass<out T>,
    private val instance: T,
) : IBinding {
    val log = Log {}
    val methods = methodsFor(contract)

    override suspend fun fireAndForget(operation: String, message: Message): Unit = try {
        withContext(message.parseContext()) {
            val method = methods[operation]
                ?: throw IllegalArgumentException("No method found for address: $operation")
            method.invoke(instance, message)
            Unit
        }
    } catch (exception: Throwable) {
        throw ProtocolException(exception) { "Failed to invoke method ${operation}: ${exception.message}" }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message = try {
        withContext(message.parseContext()) {
            val method = methods[operation]
                ?: throw IllegalArgumentException("No method found for address: $operation")
            val result = method.invoke(instance, message)
            result.encodeToMessage(method.returnType)
        }
    } catch (exception: Throwable) {
        log.warn { exception.printStackTrace() }
        throw ProtocolException(exception) { "Server: Failed to invoke method ${operation}: ${exception.message}" }
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = try {
            withContext(message.parseContext()) {
                val method = methods[operation]
                    ?: throw IllegalArgumentException("No method found for address: $operation")
                val result = method.invoke(instance, message) as Flow<*>
                result.map { it.encodeToMessage(method.flowType()) }
            }
        } catch (exception: Throwable) {
            throw ProtocolException(exception) { "Failed to invoke method ${operation}: ${exception.message}" }
        }

    private suspend fun <R> KFunction<R>.invoke(instance: T, message: Message): R {
        val arg: Any? = this.valueParameters.singleOrNull()?.type?.let { message.decodeToType(it) }
        return callSuspend(*listOfNotNull(instance, arg).toTypedArray())
    }
}

private fun Message.parseContext(): Context = try {
    val headers: Map<String, JsonObject> = header.ifEmpty { "{}" }.let { RpcFormat.decodeFromString(it) }
    headers[Context.HEADER_KEY]
        ?.let { RpcFormat.decodeFromJsonElement(it) }
        ?: Context() // Default context if not present
} catch (e: Exception) {
    throw ProtocolException(e) { "Failed to parse context from message header: ${e.message}" }
}
