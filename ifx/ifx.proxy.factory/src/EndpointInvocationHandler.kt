package ifx.proxy.factory

import ifx.protocol.contract.ExtensionPipeline
import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.argType
import ifx.protocol.contract.encodeToMessage
import ifx.protocol.contract.filters.LoggingFilter
import ifx.protocol.contract.filters.Rot13Filter
import ifx.protocol.contract.toOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction

class EndpointInvocationHandler(
    private val endpoint: IMessageHandler,
    private val extensionPipeline: ExtensionPipeline = ExtensionPipeline(
        requestFilters = listOf(Rot13Filter(), LoggingFilter("Client - Sending request")),
        responseFilters = listOf(Rot13Filter(), LoggingFilter("Client - Receiving response")),
        nextHandler = endpoint
    ),
    val formatter: StringFormat = Json
) : InvocationHandler {
    @Throws(Exception::class)
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val nonNullArgs = args ?: arrayOf()
        val continuation = nonNullArgs.continuation()
        val valueArgs = nonNullArgs.filterNot { it is Continuation<*> }
        val returnType = method.kotlinFunction!!.returnType
        val operation = method.kotlinFunction!!.toOperation()
        val request = valueArgs.firstOrNull().encodeToMessage(method.argType())
        if (continuation == null) {
            // non-suspending function, just invoke regularly
            return try {
                runBlocking {
                    extensionPipeline.invokeRemote(operation, returnType, request)
                }
            } catch (exception: Throwable) {
                throw ProtocolException(exception.cause ?: exception)
            }
        }

        return invokeSuspendFunction(continuation) outer@{
            val argumentsWithoutContinuation = args?.dropLast(1) ?: emptyList()
            try {
                val request = argumentsWithoutContinuation.firstOrNull().encodeToMessage(method.argType())
                val result = extensionPipeline.invokeRemote(operation, returnType, request)
                result
            } catch (exception: Throwable) {
                throw ProtocolException(exception.cause ?: exception)
            }
        }
    }

    private suspend fun IMessageHandler.invokeRemote(operation: String, returnType: KType, message: Message): Any? =
        when (returnType.classifier) {
            Unit::class -> fireAndForget(operation, message)
            Flow::class -> requestStream(operation, message).map { responseMessage ->
                responseMessage.body.let {
                    formatter.decodeFromString(serializer(returnType.arguments.first().type!!), it)
                }
            }

            else -> requestResponse(operation, message)
                .body.let {
                    formatter.decodeFromString(serializer(returnType), it)
                }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeSuspendFunction(continuation: Continuation<*>, block: suspend () -> T): T =
        (block as (Continuation<*>) -> T)(continuation)

    @Suppress("UNCHECKED_CAST")
    private fun Array<*>?.continuation(): Continuation<Any?>? = this?.lastOrNull() as? Continuation<Any?>

}
