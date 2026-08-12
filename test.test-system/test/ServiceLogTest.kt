import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import access.product.contract.Product
import access.product.contract.ProductCriteria
import access.product.service.ProductAccessEmulator
import ifx.actuator.LogTail
import ifx.actuator.LogTailSeverity
import ifx.host.Host
import ifx.logging.Log
import ifx.protocol.contract.ProtocolException
import ifx.protocol.contract.forService
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.proxy.contract.create
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
    fun `service logger derives its identity from the descriptor and implementation`() {
        val service = ProductAccessEmulator()
        val log = Log.forService<IProductAccess>(service).withTag("Repository")
        val message = "service-scoped log-tail entry"

        LogTail.install()
        log.info { message }

        val entry = assertNotNull(
            LogTail.logs(IProductAccessDescriptor.address).lastOrNull { it.message == message }
        )
        assertEquals(ProductAccessEmulator::class.qualifiedName, entry.serviceClassName)
        assertEquals(listOf("Repository"), entry.path)
    }

    @Test
    fun `host logs an unhandled service exception and preserves its json rpc message`() = runBlocking {
        val failureMessage = "inventory database unavailable"
        val service = FailingProductAccess(failureMessage)
        val host = Host(JsonRpcServerProtocol()).addInterceptors(LoggingInterceptor())

        LogTail.install()
        host.registerService(IProductAccessDescriptor, service).open()
        try {
            val productAccess = JsonRpcProxyFactory.forHost(host).create<IProductAccess>()

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
            host.close()
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
