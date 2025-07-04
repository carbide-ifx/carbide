package ifx.proxy.factory

import ifx.protocol.contract.IMessageHandler
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.argType
import ifx.protocol.contract.encodeToMessage
import ifx.protocol.contract.flowType
import ifx.protocol.contract.toOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

class EndpointInvocationHandler(private val messageHandler: IMessageHandler) : InvocationHandler {
    @Throws(Exception::class)
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val nonNullArgs = args ?: arrayOf()
        val continuation = nonNullArgs.continuation()
        val valueArgs = nonNullArgs.filterNot { it is Continuation<*> }
        val kMethod = method.kotlinFunction!!
        val request = runBlocking { valueArgs.firstOrNull().encodeToMessage(method.argType()) }
        if (continuation == null) {
            // non-suspending function, just invoke regularly
            return try {
                runBlocking {
                    messageHandler.invokeRemote(kMethod, request)
                }
            } catch (exception: Throwable) {
                throw ProtocolException(exception.cause ?: exception)
            }
        }

        return invokeSuspendFunction(continuation) outer@{
            val argumentsWithoutContinuation = args?.dropLast(1) ?: emptyList()
            try {
                val request = argumentsWithoutContinuation.firstOrNull().encodeToMessage(method.argType())
                val result = messageHandler.invokeRemote(kMethod, request)
                result
            } catch (exception: Throwable) {
                throw ProtocolException(exception.cause ?: exception)
            }
        }
    }

    private suspend fun IMessageHandler.invokeRemote(method: KFunction<*>, message: Message): Any? =
        when (method.returnType.classifier) {
            Unit::class -> fireAndForget(method.toOperation(), message)
            Flow::class -> requestStream(method.toOperation(), message).map { responseMessage ->
                responseMessage.body.let {
                    RpcFormat.decodeFromString(serializer(method.flowType()), it)
                }
            }

            else -> requestResponse(method.toOperation(), message)
                .body.let {
                    RpcFormat.decodeFromString(serializer(method.returnType), it)
                }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeSuspendFunction(continuation: Continuation<*>, block: suspend () -> T): T =
        (block as (Continuation<*>) -> T)(continuation)

    @Suppress("UNCHECKED_CAST")
    private fun Array<*>?.continuation(): Continuation<Any?>? = this?.lastOrNull() as? Continuation<Any?>

}
