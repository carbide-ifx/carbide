package ifx.proxy.factory

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.argType
import ifx.protocol.contract.encodeToMessage
import ifx.protocol.contract.flowType
import ifx.protocol.contract.toOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

class EndpointInvocationHandler(private val messageHandler: IBinding) : InvocationHandler {
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
                throw ProtocolException(exception.message, cause = exception.cause ?: exception)
            }
        }

        return invokeSuspendFunction(continuation) outer@{
            val argumentsWithoutContinuation = args?.dropLast(1) ?: emptyList()
            try {
                val request = argumentsWithoutContinuation.firstOrNull().encodeToMessage(method.argType())
                messageHandler.invokeRemote(kMethod, request)
            } catch (exception: Throwable) {
                throw ProtocolException(exception.message, cause = exception.cause ?: exception)
            }
        }
    }

    private suspend fun IBinding.invokeRemote(method: KFunction<*>, message: Message): Any? =
        when (method.returnType.classifier) {
            Unit::class -> fireAndForget(method.toOperation(), message)
            Flow::class -> requestStream(method.toOperation(), message)
                .map { responseMessage ->
                    RpcFormat.decodeFromString(serializer(method.flowType()), responseMessage.body)
                }.catch { exception ->
                    throw ProtocolException(exception.message, cause = exception.cause ?: exception)
                }
            else -> requestResponse(method.toOperation(), message).body.let {
                RpcFormat.decodeFromString(serializer(method.returnType), it)
            }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeSuspendFunction(continuation: Continuation<*>, block: suspend () -> T): T =
        (block as (Continuation<*>) -> T)(continuation)

    @Suppress("UNCHECKED_CAST")
    private fun Array<*>?.continuation(): Continuation<Any?>? = this?.lastOrNull() as? Continuation<Any?>

}
