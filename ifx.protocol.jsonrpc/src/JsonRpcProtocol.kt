package ifx.protocol.jsonrpc

import ifx.host.IServerProtocol
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.ServiceEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

const val JSON_RPC_PROTOCOL_ID: String = "json-rpc"

private const val IFX_HEADERS = "Ifx-Message-Headers"

class JsonRpcServerProtocol : IServerProtocol {
    override val id: String = JSON_RPC_PROTOCOL_ID

    override fun install(application: Application, endpoints: List<Endpoint>) {
        application.routing {
            endpoints.forEach { endpoint ->
                post("/${endpoint.address}") {
                    call.handleJsonRpc(endpoint)
                }
            }
        }
    }

    private suspend fun ApplicationCall.handleJsonRpc(endpoint: Endpoint) {
        val request = try {
            parseRequest(receiveText())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            respondError(JsonNull, -32700, "Parse error")
            return
        }

        if (request.jsonrpc != "2.0" || request.method == null) {
            respondError(request.id ?: JsonNull, -32600, "Invalid Request")
            return
        }

        val operation = endpoint.description.operations.singleOrNull { it.route == request.method }
        if (operation == null) {
            if (request.hasId) respondError(request.id ?: JsonNull, -32601, "Method not found")
            else respondText("", status = HttpStatusCode.NoContent)
            return
        }
        if (operation.interaction == InteractionType.REQUEST_STREAM) {
            if (request.hasId) {
                respondError(request.id ?: JsonNull, -32601, "Streaming is not supported by JSON-RPC over HTTP")
            } else {
                respondText("", status = HttpStatusCode.NoContent)
            }
            return
        }

        val message = try {
            Message(
                header = request.headers[IFX_HEADERS]?.let(::decodeHeaders) ?: "{}",
                body = request.params?.toString().orEmpty(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            if (request.hasId) respondError(request.id ?: JsonNull, -32600, "Invalid message headers")
            else respondText("", status = HttpStatusCode.NoContent)
            return
        }

        try {
            when (operation.interaction) {
                InteractionType.FIRE_AND_FORGET -> {
                    endpoint.binding.fireAndForget(request.method, message)
                    if (request.hasId) respondResult(request.id ?: JsonNull, JsonNull, "{}")
                    else respondText("", status = HttpStatusCode.NoContent)
                }

                InteractionType.REQUEST_RESPONSE -> {
                    val result = endpoint.binding.requestResponse(request.method, message)
                    if (request.hasId) {
                        respondResult(
                            request.id ?: JsonNull,
                            result.body.toJsonElementOrNull() ?: JsonNull,
                            result.header,
                        )
                    } else {
                        respondText("", status = HttpStatusCode.NoContent)
                    }
                }

                InteractionType.REQUEST_STREAM -> error("Handled above")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            if (request.hasId) respondError(request.id ?: JsonNull, -32603, "Internal error")
            else respondText("", status = HttpStatusCode.NoContent)
        }
    }
}

/**
 * Creates JSON-RPC bindings that share one ktor client. [close] releases that client only when this
 * protocol created it; a caller-supplied client stays under the caller's ownership.
 */
class JsonRpcClientProtocol private constructor(
    private val baseUrl: () -> String,
    private val httpClient: HttpClient,
    private val ownsHttpClient: Boolean,
) : IClientProtocol {
    constructor(baseUrl: () -> String) : this(baseUrl, HttpClient(), ownsHttpClient = true)

    constructor(baseUrl: () -> String, httpClient: HttpClient) :
        this(baseUrl, httpClient, ownsHttpClient = false)

    constructor(host: String, port: Int) : this({ "http://$host:$port" })

    override fun createClientBinding(address: String, endpoint: ServiceEndpoint?): IBinding {
        val base = endpoint?.let { "http://${it.host}:${it.port}" } ?: baseUrl()
        return JsonRpcClient(httpClient, "${base.trimEnd('/')}/$address")
    }

    override fun close() {
        if (ownsHttpClient) httpClient.close()
    }
}

private class JsonRpcClient(
    private val httpClient: HttpClient,
    private val url: String,
) : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) {
        httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header(IFX_HEADERS, encodeHeaders(message.header))
            setBody(requestBody(operation, message, id = null).toString())
        }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header(IFX_HEADERS, encodeHeaders(message.header))
            setBody(requestBody(operation, message, id = JsonPrimitive("1")).toString())
        }
        val body = try {
            RpcFormat.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Throwable) {
            throw ProtocolException(exception) { "Invalid JSON-RPC response from $url" }
        }
        body["error"]?.jsonObject?.let { error ->
            val code = error["code"]?.jsonPrimitive?.contentOrNull
            val messageText = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown JSON-RPC error"
            throw ProtocolException("JSON-RPC error $code: $messageText", null)
        }
        val result = body["result"] ?: throw ProtocolException("JSON-RPC response contains no result", null)
        return Message(
            header = response.headers[IFX_HEADERS]?.let(::decodeHeaders) ?: "{}",
            body = result.toString(),
        )
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
        throw ProtocolException("Streaming is not supported by JSON-RPC over HTTP: $operation", null)
    }
}

private data class JsonRpcRequest(
    val jsonrpc: String?,
    val method: String?,
    val params: JsonElement?,
    val id: JsonElement?,
    val hasId: Boolean,
    val headers: io.ktor.http.Headers,
)

private fun ApplicationCall.parseRequest(body: String): JsonRpcRequest {
    val json = RpcFormat.parseToJsonElement(body).jsonObject
    return JsonRpcRequest(
        jsonrpc = json["jsonrpc"]?.jsonPrimitive?.contentOrNull,
        method = json["method"]?.jsonPrimitive?.contentOrNull,
        params = json["params"],
        id = json["id"],
        hasId = "id" in json,
        headers = request.headers,
    )
}

private fun requestBody(operation: String, message: Message, id: JsonElement?): JsonObject = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("method", JsonPrimitive(operation))
    message.body.toJsonElementOrNull()?.let { put("params", it) }
    if (id != null) put("id", id)
}

private suspend fun ApplicationCall.respondResult(id: JsonElement, result: JsonElement, headers: String) {
    response.headers.append(IFX_HEADERS, encodeHeaders(headers))
    respondText(
        buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("result", result)
            put("id", id)
        }.toString(),
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.respondError(id: JsonElement, code: Int, message: String) {
    respondText(
        buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("error", buildJsonObject {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            })
            put("id", id)
        }.toString(),
        ContentType.Application.Json,
    )
}

private fun String.toJsonElementOrNull(): JsonElement? =
    takeIf(String::isNotBlank)?.let(RpcFormat::parseToJsonElement)

private fun encodeHeaders(headers: String): String = Base64.Default.encode(headers.encodeToByteArray())

private fun decodeHeaders(headers: String): String = Base64.Default.decode(headers).decodeToString()
