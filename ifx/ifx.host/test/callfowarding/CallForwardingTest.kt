package ifx.host.callfowarding

import ifx.context.Context
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.logging.Log
import ifx.protocol.rsocket.RSocketProtocolServer
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import ifx.test.assertSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test


val protocol = RSocketProtocolServer()
val proxyFactory = ProxyFactory(protocol)
val manager = SomeManager(proxyFactory)
val engine = SomeEngine(proxyFactory)
val ra = SomeResourceAccess(proxyFactory)
val host =
    Host().addProtocol(protocol).registerService<ISomeManager> { manager }.registerService<ISomeEngine> { engine }
        .registerService<ISomeResourceAccess> { ra }.start()

class CallForwardingTest {

    @Test fun logTest() {
        Log.info { "Call Forwarding" }
    }

    @Test
    fun `Invoke use case`() = runTest {
        val managerProxy = proxyFactory.create<ISomeManager>()
        withContext(Context("A TEST TRACE ID")) {
            val response = managerProxy.someUseCase(CallRequest(listOf("a", "b", "c"))).assertSuccess()
            val expected = listOf("a", "b", "c", "resource response", "engine response", "manager response")
            response shouldBe CallResponse(expected)
        }
    }

    @Test
    fun `Blocking use case`() = runTest {
        val managerProxy = proxyFactory.create<ISomeManager>()
        withContext(Context("A TEST TRACE ID")) {
            val response = managerProxy.someBlockingUseCase(CallRequest(listOf("a", "b", "c"))).assertSuccess()
            val expected = listOf("a", "b", "c", "resource response", "engine response", "manager response")
            response shouldBe CallResponse(expected)
        }
    }

}
