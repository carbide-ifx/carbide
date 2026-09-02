import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.Product
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import ifx.actuator.LogTail
import ifx.actuator.LogTailSeverity
import ifx.host.Host
import ifx.protocol.contract.ProtocolException
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.proxy.factory.create
import ifx.proxy.factory.jsonrpc.JsonRpcProxyFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ServiceLogTest {
    @Test
    fun `service logger derives its identity from the descriptor and implementation`() = runBlocking {
        val service = ProductAccessEmulator()
        val host = Host(JsonRpcServerProtocol())

        LogTail.install()
        host.registerService(IProductAccessDescriptor, service).start()
        val proxyFactory = JsonRpcProxyFactory.forHost(host)
        try {
            proxyFactory.create<IProductAccess>().filter(ProductCriteria())

            val entry = assertNotNull(
                LogTail.logs(IProductAccessDescriptor.address).lastOrNull { it.message == "Found 0 products" }
            )
            assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
            assertEquals(emptyList(), entry.path)
        } finally {
            proxyFactory.close()
            host.stop()
        }
    }

    @Test
    fun `host logs an unhandled service exception and preserves its json rpc message`() = runBlocking {
        val failureMessage = "inventory database unavailable"
        val service = FailingProductAccess(failureMessage)
        val host = Host(JsonRpcServerProtocol())

        LogTail.install()
        host.registerService(IProductAccessDescriptor, service).start()
        val proxyFactory = JsonRpcProxyFactory.forHost(host)
        try {
            val productAccess = proxyFactory.create<IProductAccess>()

            val exception = assertFailsWith<ProtocolException> {
                productAccess.filter(ProductCriteria())
            }

            assertContains(exception.message.orEmpty(), failureMessage)
            val entry = assertNotNull(
                LogTail.logs(IProductAccessDescriptor.address)
                    .lastOrNull { it.throwable?.contains(failureMessage) == true }
            )
            assertEquals(FailingProductAccess::class.qualifiedName, entry.serviceClassName)
            assertEquals(listOf("filter(access.product.contract.ProductCriteria)"), entry.path)
            assertEquals(LogTailSeverity.Error, entry.severity)
        } finally {
            proxyFactory.close()
            host.stop()
        }
    }
}

private class FailingProductAccess(
    private val failureMessage: String,
) : IProductAccess {
    override suspend fun filter(criteria: ProductCriteria): List<Product> = error(failureMessage)

    override fun generateRandowProduct(): Flow<Product> = emptyFlow()

    override suspend fun store(product: Product) = Unit

    override suspend fun notifyProductViewed(productId: String) = Unit
}
