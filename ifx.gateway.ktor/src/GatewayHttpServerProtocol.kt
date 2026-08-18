package ifx.gateway.ktor

import ifx.context.Context
import ifx.gateway.contract.GatewayFailure
import ifx.gateway.contract.GatewayFailureException
import ifx.host.IServerProtocol
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.RpcFormat
import ifx.protocol.contract.TypeReference
import ifx.protocol.contract.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val GATEWAY_HTTP_PROTOCOL_ID: String = "gateway-http"

/** Returns a trusted context, or null when this request is not authenticated. */
fun interface GatewayAuthenticator {
    suspend fun authenticate(call: ApplicationCall): Context?
}

data class GatewayHttpDeployment(
    val title: String? = null,
    val apiVersion: String = "1.0.0",
    val serverUrls: List<String> = emptyList(),
)

/** Conventional HTTP adapter for projected gateway endpoints. */
class GatewayHttpServerProtocol(
    private val authenticator: GatewayAuthenticator,
    private val prefix: String = "/api",
    private val deployment: GatewayHttpDeployment = GatewayHttpDeployment(),
) : IServerProtocol {
    override val id: String = GATEWAY_HTTP_PROTOCOL_ID

    init {
        require(prefix.startsWith('/')) { "Gateway HTTP prefix must start with /" }
        require(prefix.length > 1 && !prefix.endsWith('/')) {
            "Gateway HTTP prefix must contain a path segment and must not end with /"
        }
    }

    override fun install(application: Application, endpoints: List<Endpoint>) {
        application.routing {
            endpoints.forEach { endpoint ->
                val basePath = "$prefix/${endpoint.address}"
                get("$basePath/openapi.json") {
                    call.respondText(
                        endpoint.openApiDocument(prefix, deployment).toString(),
                        ContentType.Application.Json,
                    )
                }
                endpoint.description.operations.forEach { operation ->
                    post("$basePath/${operation.route}") {
                        call.handle(endpoint, operation.route, operation.interaction, operation.request)
                    }
                }
                post("$basePath/{unexposed...}") {
                    call.respondFailure(
                        HttpStatusCode.NotFound,
                        GatewayFailure("operation_not_found", "Gateway operation is not exposed"),
                    )
                }
            }
        }
    }

    private suspend fun ApplicationCall.handle(
        endpoint: Endpoint,
        operation: String,
        interaction: InteractionType,
        requestType: TypeReference,
    ) {
        val context = try {
            authenticator.authenticate(this)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (context == null) {
            respondFailure(HttpStatusCode.Unauthorized, GatewayFailure("unauthorized", "Authentication required"))
            return
        }

        val body = receiveText()
        if (requestType !is TypeReference.VoidType && body.isBlank()) {
            respondFailure(HttpStatusCode.BadRequest, GatewayFailure("invalid_request", "A JSON request body is required"))
            return
        }
        if (body.isNotBlank()) {
            try {
                RpcFormat.parseToJsonElement(body)
            } catch (_: SerializationException) {
                respondFailure(HttpStatusCode.BadRequest, GatewayFailure("invalid_request", "Request body is not valid JSON"))
                return
            }
        }
        val message = Message(header = "{}", body = body).withContext(context)

        try {
            when (interaction) {
                InteractionType.FIRE_AND_FORGET -> {
                    endpoint.binding.fireAndForget(operation, message)
                    respondText("", status = HttpStatusCode.Accepted)
                }

                InteractionType.REQUEST_RESPONSE -> {
                    val response = endpoint.binding.requestResponse(operation, message)
                    respondText(
                        response.body.ifBlank { "null" },
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }

                InteractionType.REQUEST_STREAM -> respondStream(endpoint, operation, message)
            }
        } catch (failure: GatewayFailureException) {
            respondFailure(failure.failure.httpStatus(), failure.failure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            respondFailure(
                HttpStatusCode.InternalServerError,
                GatewayFailure("internal_error", "The request could not be completed"),
            )
        }
    }

    private suspend fun ApplicationCall.respondStream(
        endpoint: Endpoint,
        operation: String,
        message: Message,
    ) {
        respondBytesWriter(NDJSON, HttpStatusCode.OK) {
            try {
                endpoint.binding.requestStream(operation, message).collect { response ->
                    val data = response.body.ifBlank { "null" }
                    val event = buildJsonObject {
                        put("type", JsonPrimitive("next"))
                        put("data", RpcFormat.parseToJsonElement(data))
                    }
                    writeStringUtf8("$event\n")
                    flush()
                }
                writeStringUtf8("{\"type\":\"complete\"}\n")
                flush()
            } catch (failure: GatewayFailureException) {
                writeStringUtf8(errorEvent(failure.failure))
                flush()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                writeStringUtf8(errorEvent(GatewayFailure("internal_error", "The stream failed")))
                flush()
            }
        }
    }
}

private val NDJSON = ContentType.parse("application/x-ndjson")

private fun GatewayFailure.httpStatus(): HttpStatusCode = when (code) {
    "operation_not_found" -> HttpStatusCode.NotFound
    "unauthorized" -> HttpStatusCode.Unauthorized
    "forbidden" -> HttpStatusCode.Forbidden
    "internal_error" -> HttpStatusCode.InternalServerError
    else -> HttpStatusCode.BadRequest
}

private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, failure: GatewayFailure) {
    respondText(
        RpcFormat.encodeToString(failure),
        ContentType.Application.Json,
        status,
    )
}

private fun errorEvent(failure: GatewayFailure): String = buildJsonObject {
    put("type", JsonPrimitive("error"))
    put("error", RpcFormat.parseToJsonElement(RpcFormat.encodeToString(failure)))
}.toString() + "\n"
