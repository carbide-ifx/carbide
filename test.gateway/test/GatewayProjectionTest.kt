package test.gateway

import ifx.gateway.bind
import ifx.gateway.endpointSource
import ifx.gateway.contract.GatewayFailureException
import ifx.gateway.typescript.renderTypeScriptSdk
import ifx.gateway.ktor.renderOpenApi
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.TypeDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GatewayProjectionTest {
    @Test
    fun `surface exposes selected manager operations without repeating their contracts`() {
        assertEquals("product-web", ProductWebApi.name)
        assertEquals(
            mapOf(
                "productAccess" to listOf("filter", "generateRandowProduct"),
                "sales" to listOf("listProducts"),
            ),
            ProductWebApi.services.associate { service ->
                service.name to service.operations.map { operation -> operation.name }
            },
        )
    }

    @Test
    fun `projected endpoint forwards an allowed public operation to its manager route`() = runBlocking {
        val target = RecordingBinding()
        val endpoint = ProductWebApi.bind { target }
        val request = Message(header = "{\"trace\":\"abc\"}", body = "{\"ids\":[\"42\"]}")

        val response = endpoint.binding.requestResponse("productAccess/filter", request)

        assertEquals(
            RecordedCall(
                operation = "filter(access.product.contract.ProductCriteria)",
                message = request,
            ),
            target.lastCall,
        )
        assertEquals(Message("{}", "{\"accepted\":true}"), response)
    }

    @Test
    fun `endpoint source projects registered local manager bindings into one public endpoint`() = runBlocking {
        val productTarget = RecordingBinding()
        val salesTarget = RecordingBinding()
        val registered = ProductWebApi.services.map { service ->
            Endpoint(
                binding = if (service.name == "productAccess") productTarget else salesTarget,
                description = service.descriptor.description,
            )
        }

        val endpoints = ProductWebApi.endpointSource().endpoints(registered)
        endpoints.single().binding.requestResponse("productAccess/filter", Message("{}", "{}"))

        assertEquals("product-web", endpoints.single().address)
        assertEquals(
            "filter(access.product.contract.ProductCriteria)",
            productTarget.lastCall?.operation,
        )
        assertEquals(null, salesTarget.lastCall)
    }

    @Test
    fun `projected TypeScript SDK preserves DTOs and groups only exposed operations`() {
        val source = ProductWebApi.renderTypeScriptSdk()

        assertTrue(source.contains("readonly productAccess"))
        assertTrue(source.contains("filter(criteria: ProductCriteria)"))
        assertTrue(source.contains("generateRandowProduct(): AsyncIterable<access_product_contract_Product>"))
        assertTrue(source.contains("readonly sales"))
        assertTrue(source.contains("listProducts(): AsyncIterable<manager_sales_contract_Product>"))
        assertTrue(source.contains("\"productAccess/filter\""))
        assertFalse(source.contains("notifyProductViewed"))
        assertFalse(source.contains("productAccess/store"))
    }

    @Test
    fun `projected TypeScript SDK renders JVM inline value classes as their wire type`() {
        val productId = ProductWebApi.services
            .flatMap { service -> service.descriptor.description.types }
            .single { type -> type.name == "access.product.contract.ProductId" }
        val source = ProductWebApi.renderTypeScriptSdk()

        assertTrue(productId is TypeDescription.Alias)
        assertTrue(source.contains("export type ProductId = string;"))
        assertFalse(source.contains("export interface ProductId"))
    }

    @Test
    fun `manager failures cross the public binding as a safe gateway failure`() = runBlocking {
        val endpoint = ProductWebApi.bind { FailingBinding }

        val failure = assertFailsWith<GatewayFailureException> {
            endpoint.binding.requestResponse("productAccess/filter", Message("{}", "{}"))
        }

        assertEquals("internal_error", failure.failure.code)
        assertFalse(failure.message.orEmpty().contains("database-password"))
    }

    @Test
    fun `OpenAPI can be emitted as a deployment artifact without starting a host`() {
        val document = ProductWebApi.renderOpenApi()

        assertTrue(document.contains("\"openapi\":\"3.1.0\""))
        assertTrue(document.contains("/api/product-web/productAccess/filter"))
        assertFalse(document.contains("productAccess/store"))
    }

    @Test
    fun `typed operations can override their public convention without string binding`() = runBlocking {
        val target = RecordingBinding()
        val endpoint = AliasedProductWebApi.bind { target }

        endpoint.binding.requestResponse("productAccess/find", Message("{}", "{}"))

        assertEquals("product-web/v2", endpoint.address)
        assertEquals("filter(access.product.contract.ProductCriteria)", target.lastCall?.operation)
    }

    @Test
    fun `standalone projection resolves typed remote targets without local registrations`() = runBlocking {
        val protocol = RecordingClientProtocol()
        val endpoint = productWebRemoteEndpointSource(protocol).endpoints(emptyList()).single()

        endpoint.binding.requestResponse("productAccess/filter", Message("{}", "{}"))

        assertEquals(
            ProductWebApi.services.map { service -> service.descriptor.address },
            protocol.addresses,
        )
        assertEquals("filter(access.product.contract.ProductCriteria)", protocol.target.lastCall?.operation)
    }
}

private data class RecordedCall(
    val operation: String,
    val message: Message,
)

private class RecordingBinding : IBinding {
    var lastCall: RecordedCall? = null

    override suspend fun fireAndForget(operation: String, message: Message) {
        lastCall = RecordedCall(operation, message)
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        lastCall = RecordedCall(operation, message)
        return Message("{}", "{\"accepted\":true}")
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        lastCall = RecordedCall(operation, message)
        return emptyFlow()
    }
}

private object FailingBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = error("database-password")
    override suspend fun requestResponse(operation: String, message: Message): Message = error("database-password")
    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = error("database-password")
}

private class RecordingClientProtocol : IClientProtocol {
    val addresses = mutableListOf<String>()
    val target = RecordingBinding()

    override fun createClientBinding(address: String, endpoint: ServiceEndpoint?): IBinding {
        addresses += address
        return target
    }

    override fun close() = Unit
}
