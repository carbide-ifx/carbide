package ifx.protocol.rsocket

import ifx.context.Context
import ifx.protocol.contract.ProtocolException
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.metadata
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

class RsocketInvocationHandler(private val rSocket: RSocket, val formatter: StringFormat = Json) : InvocationHandler {
    @Throws(Exception::class)
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val nonNullArgs = args ?: arrayOf()
        val continuation = nonNullArgs.continuation()
        val returnType = method.kotlinFunction!!.returnType
        if (continuation == null) {
            // non-suspending function, just invoke regularly
            return try {
                runBlocking {
                    val payload: Payload = method.toPayload(nonNullArgs.firstOrNull())
                    rSocket.invokeRemote(returnType, payload)
                }
            } catch (exception: Throwable) {
                throw ProtocolException(exception.cause ?: exception)
            }
        }

        return invokeSuspendFunction(continuation) outer@{
            val argumentsWithoutContinuation = args?.dropLast(1) ?: emptyList()
            try {
                val payload = method.toPayload(argumentsWithoutContinuation.firstOrNull())
                val result = rSocket.invokeRemote(returnType, payload)
                result
            } catch (exception: Throwable) {
                throw exception.cause ?: exception
            }
        }
    }

    private suspend fun RSocket.invokeRemote(returnType: KType, payload: Payload): Any? = when (returnType.classifier) {
        Unit::class -> fireAndForget(payload)
        Flow::class -> requestStream(payload).map { payload ->
            payload.data.readString()
                .let {
                    formatter.decodeFromString(serializer(returnType.arguments.first().type!!), it)
                }
        }
        else -> requestResponse(payload)
            .data.readString()
            .let {
                formatter.decodeFromString(serializer(returnType), it)
            }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeSuspendFunction(continuation: Continuation<*>, block: suspend () -> T): T =
        (block as (Continuation<*>) -> T)(continuation)

    @Suppress("UNCHECKED_CAST")
    private fun Array<*>?.continuation(): Continuation<Any?>? = this?.lastOrNull() as? Continuation<Any?>


    @OptIn(ExperimentalMetadataApi::class)
    private fun Method.toPayload(arg: Any?, context: Context = Context()) = buildPayload {
        val route = this@toPayload.kotlinFunction!!.operationName()
        metadata(RoutingMetadata(route)) // todo: Add context to metadata (headers)
        val body = arg?.let {
            val argType = this@toPayload.kotlinFunction!!.valueParameters.single().type
            formatter.encodeToString(serializer(argType), arg)
        }
        data(body ?: "")
    }
}
