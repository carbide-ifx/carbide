package ifx.gateway.ktor

import ifx.gateway.bind
import ifx.gateway.contract.GatewayProjection
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.TypeDescription
import ifx.protocol.contract.TypeReference
import ifx.protocol.contract.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Generates the same OpenAPI document served by [GatewayHttpServerProtocol], without opening a host. */
fun GatewayProjection.renderOpenApi(
    prefix: String = "/api",
    deployment: GatewayHttpDeployment = GatewayHttpDeployment(),
): String = bind { OpenApiOnlyBinding }.openApiDocument(prefix, deployment).toString()

internal fun Endpoint.openApiDocument(
    prefix: String,
    deployment: GatewayHttpDeployment,
): JsonObject = buildJsonObject {
    put("openapi", JsonPrimitive("3.1.0"))
    put("info", buildJsonObject {
        put("title", JsonPrimitive(deployment.title ?: description.name))
        put("version", JsonPrimitive(deployment.apiVersion))
    })
    if (deployment.serverUrls.isNotEmpty()) {
        put("servers", buildJsonArray {
            deployment.serverUrls.forEach { url -> add(buildJsonObject { put("url", JsonPrimitive(url)) }) }
        })
    }
    put("paths", buildJsonObject {
        description.operations.forEach { operation ->
            val path = "$prefix/$address/${operation.route}"
            put(path, buildJsonObject { put("post", operation.openApiOperation()) })
        }
    })
    put("components", buildJsonObject {
        put("schemas", buildJsonObject {
            description.types.forEach { type -> put(type.name, type.toSchema()) }
            put("GatewayFailure", gatewayFailureSchema())
        })
    })
}

private fun OperationDescription.openApiOperation(): JsonObject = buildJsonObject {
    put("operationId", JsonPrimitive(route.replace('/', '.')))
    put("responses", buildJsonObject {
        when (interaction) {
            InteractionType.FIRE_AND_FORGET -> put("202", response("Accepted"))
            InteractionType.REQUEST_RESPONSE -> put("200", response("Successful response", response))
            InteractionType.REQUEST_STREAM -> put("200", ndjsonResponse(response))
        }
        put("400", failureResponse("Invalid request"))
        put("401", failureResponse("Authentication required"))
        put("500", failureResponse("Internal error"))
    })
    if (request !is TypeReference.VoidType) {
        put("requestBody", buildJsonObject {
            put("required", JsonPrimitive(true))
            put("content", content("application/json", request.toSchema()))
        })
    }
}

private fun response(description: String, type: TypeReference? = null): JsonObject = buildJsonObject {
    put("description", JsonPrimitive(description))
    if (type != null) put("content", content("application/json", type.toSchema()))
}

private fun ndjsonResponse(type: TypeReference): JsonObject = buildJsonObject {
    put("description", JsonPrimitive("Newline-delimited stream of next, complete, or error events"))
    put("content", content("application/x-ndjson", buildJsonObject {
        put("oneOf", buildJsonArray {
            add(streamEvent("next", "data", type.toSchema()))
            add(streamEvent("complete"))
            add(streamEvent("error", "error", reference("GatewayFailure")))
        })
        put("discriminator", buildJsonObject { put("propertyName", JsonPrimitive("type")) })
    }))
}

private fun streamEvent(type: String, valueName: String? = null, valueSchema: JsonElement? = null): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("type", buildJsonObject { put("const", JsonPrimitive(type)) })
            if (valueName != null && valueSchema != null) put(valueName, valueSchema)
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("type"))
            if (valueName != null) add(JsonPrimitive(valueName))
        })
    }

private fun failureResponse(description: String): JsonObject = buildJsonObject {
    put("description", JsonPrimitive(description))
    put("content", content("application/json", reference("GatewayFailure")))
}

private fun content(mediaType: String, schema: JsonElement): JsonObject = buildJsonObject {
    put(mediaType, buildJsonObject { put("schema", schema) })
}

private fun TypeReference.toSchema(): JsonObject = when (this) {
    TypeReference.StringType -> primitiveSchema("string")
    TypeReference.NumberType -> primitiveSchema("number")
    TypeReference.BooleanType -> primitiveSchema("boolean")
    TypeReference.VoidType -> buildJsonObject { put("type", JsonPrimitive("null")) }
    is TypeReference.Parameter -> buildJsonObject {}
    is TypeReference.Named -> reference(name)
    is TypeReference.ArrayType -> buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("items", element.toSchema())
    }
    is TypeReference.RecordType -> buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", value.toSchema())
    }
    is TypeReference.Nullable -> buildJsonObject {
        put("anyOf", JsonArray(listOf(value.toSchema(), buildJsonObject { put("type", JsonPrimitive("null")) })))
    }
}

private fun TypeDescription.toSchema(): JsonObject = when (this) {
    is TypeDescription.ObjectType -> buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            properties.forEach { property -> put(property.name, property.type.toSchema()) }
        })
        val required = properties.filterNot { property -> property.optional }.map { property -> JsonPrimitive(property.name) }
        if (required.isNotEmpty()) put("required", JsonArray(required))
    }
    is TypeDescription.StringUnion -> buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("enum", JsonArray(values.map(::JsonPrimitive)))
    }
    is TypeDescription.SealedUnion -> buildJsonObject {
        put("oneOf", JsonArray(variants.map { variant -> variant.type.toSchema() }))
        put("discriminator", buildJsonObject { put("propertyName", JsonPrimitive(discriminator)) })
    }
    is TypeDescription.Alias -> target.toSchema()
}

private fun gatewayFailureSchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("properties", buildJsonObject {
        put("code", primitiveSchema("string"))
        put("message", primitiveSchema("string"))
        put("details", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", primitiveSchema("string"))
        })
    })
    put("required", JsonArray(listOf(JsonPrimitive("code"), JsonPrimitive("message"))))
}

private fun primitiveSchema(type: String): JsonObject = buildJsonObject { put("type", JsonPrimitive(type)) }

private fun reference(name: String): JsonObject = buildJsonObject {
    put("\$ref", JsonPrimitive("#/components/schemas/${name.replace("~", "~0").replace("/", "~1")}"))
}

private object OpenApiOnlyBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit
    override suspend fun requestResponse(operation: String, message: Message): Message = message
    override fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}
