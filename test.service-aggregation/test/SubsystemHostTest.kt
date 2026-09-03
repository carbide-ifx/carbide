package test.service.aggregation

import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.Product
import access.product.contract.ProductCriteria
import access.product.contract.ProductId
import ifx.host.Host
import ifx.host.IServerProtocol
import ifx.context.Context
import ifx.logging.Log
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.headers
import ifx.proxy.factory.IProxyFactory
import ifx.service.IService
import ifx.stdlib.TimeSpan
import ifx.subsystem.development
import io.ktor.server.application.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsystemHostTest {
    @Test
    fun `subsystem exports its public service programming surface`() {
        val headers: Map<String, JsonElement> = Message("{}", "").headers()
        val context: Context = Context.Empty
        val log: Log = ExportedService().log
        val timeSpan: TimeSpan? = null

        assertTrue(headers.isEmpty())
        assertTrue(context.isEmpty)
        assertTrue(log.tag.isNotBlank())
        assertEquals(null, timeSpan)
    }

    @Test
    fun `development host includes the standard subsystem tooling`() = runBlocking {
        val host = Host.development()

        assertTrue(dependencyServiceDescriptor.address.isNotBlank())
        assertEquals("Service Host", host.name)
        assertEquals(listOf("IActuator"), host.serviceCatalog().services.map { it.name })
    }

    @Test
    fun `generated descriptor exposes typed operation metadata`() {
        assertEquals("filter(access.product.contract.ProductCriteria)", dependencyFilterOperation.description.route)
        assertEquals("generateRandowProduct()", dependencyStreamOperation.description.route)
        assertEquals("notifyProductViewed(kotlin.String)", dependencyFireAndForgetOperation.description.route)
    }

    @Test
    fun `compiler plugin rewrites typed registration and proxy creation`() = runBlocking {
        val host = Host(NoopServerProtocol)
        registerDependencyService(host, ProductAccessFixture)
        val proxyFactory = RecordingProxyFactory()

        dependencyServiceProxy(proxyFactory)

        assertTrue(host.serviceCatalog().services.any { it.address == IProductAccessDescriptor.address })
        assertEquals(IProductAccessDescriptor.address, proxyFactory.createdAddress)
    }
}

private class ExportedService : IService

private object ProductAccessFixture : IProductAccess {
    override suspend fun filter(criteria: ProductCriteria): List<Product> = emptyList()
    override suspend fun findById(request: access.product.contract.FindByIdRequest): ProductId? = null
    override fun generateRandowProduct(): Flow<Product> = emptyFlow()
    override suspend fun store(product: Product) = Unit
    override suspend fun notifyProductViewed(productId: String) = Unit
}

private class RecordingBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit

    override suspend fun requestResponse(operation: String, message: Message): Message = message

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}

private class RecordingProxyFactory : IProxyFactory {
    var createdAddress: String? = null

    override fun <T : IService> create(descriptor: ServiceDescriptor<T>): T {
        createdAddress = descriptor.address
        return descriptor.createClient(RecordingBinding())
    }

    override fun at(endpoint: ServiceEndpoint): IProxyFactory = this
    override fun addInterceptors(vararg i: IInterceptor): IProxyFactory = this
    override fun addInterceptors(i: List<IInterceptor>): IProxyFactory = this
    override fun close() = Unit
}

private object NoopServerProtocol : IServerProtocol {
    override val id: String = "noop"
    override fun install(application: Application, endpoints: List<ifx.protocol.contract.Endpoint>) = Unit
}
