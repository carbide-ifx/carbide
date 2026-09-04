package test.gateway

import ifx.context.Context
import ifx.gateway.bind
import ifx.gateway.ktor.GatewayAuthenticator
import ifx.gateway.ktor.GatewayHttpServerProtocol
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayHttpTest {
    @Test
    fun `conventional HTTP publishes projected operations and generated OpenAPI`() = testApplication {
        val product = HttpRecordingBinding()
        val sales = HttpRecordingBinding(
            stream = flowOf(Message("{}", "{\"id\":\"42\",\"description\":\"Tea\",\"price\":10}")),
        )
        val endpoint = ProductWebApi.bind { descriptor ->
            if (descriptor.description.name == "IProductAccess") product else sales
        }
        application {
            GatewayHttpServerProtocol(
                authenticator = GatewayAuthenticator { Context.Empty },
            ).install(this, listOf(endpoint))
        }

        val response = client.post("/api/product-web/productAccess/filter") {
            contentType(ContentType.Application.Json)
            setBody("{\"ids\":[\"42\"]}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{\"accepted\":true}", response.bodyAsText())
        assertEquals("filter(access.product.contract.ProductCriteria)", product.operation)

        val stream = client.post("/api/product-web/sales/listProducts")
        assertEquals(HttpStatusCode.OK, stream.status)
        assertEquals(
            listOf(
                "{\"type\":\"next\",\"data\":{\"id\":\"42\",\"description\":\"Tea\",\"price\":10}}",
                "{\"type\":\"complete\"}",
            ),
            stream.bodyAsText().lineSequence().filter(String::isNotBlank).toList(),
        )

        val hidden = client.post("/api/product-web/productAccess/store") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, hidden.status)
        assertTrue(hidden.bodyAsText().contains("operation_not_found"))

        val openApi = client.get("/api/product-web/openapi.json").bodyAsText()
        assertTrue(openApi.contains("/api/product-web/productAccess/filter"))
        assertTrue(openApi.contains("access.product.contract.ProductCriteria"))
        assertFalse(openApi.contains("productAccess/store"))
    }
}

private class HttpRecordingBinding(
    private val stream: Flow<Message> = flowOf(),
) : IBinding {
    var operation: String? = null

    override suspend fun fireAndForget(operation: String, message: Message) {
        this.operation = operation
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        this.operation = operation
        return Message("{}", "{\"accepted\":true}")
    }

    override fun requestStream(operation: String, message: Message): Flow<Message> {
        this.operation = operation
        return stream
    }
}
