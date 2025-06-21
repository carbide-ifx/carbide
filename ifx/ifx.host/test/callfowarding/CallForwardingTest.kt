package ifx.host.callfowarding

import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.host.contract.CallRequest
import ifx.host.contract.CallResponse
import ifx.host.contract.ISomeEngine
import ifx.host.contract.ISomeManager
import ifx.host.contract.ISomeResourceAccess
import ifx.host.contract.SomeEngine
import ifx.host.contract.SomeManager
import ifx.host.contract.SomeResourceAccess
import ifx.protocol.rsocket.RSocketEndpoint
import ifx.proxy.factory.ProxyFactory
import ifx.test.assertSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


val protocol = RSocketEndpoint()
val proxyFactory = ProxyFactory(protocol)
val manager = SomeManager(proxyFactory)
val engine = SomeEngine(proxyFactory)
val ra = SomeResourceAccess(proxyFactory)
val host = Host()
    .addProtocol(protocol)
    .registerService<ISomeManager> { manager }
    .registerService<ISomeEngine> { engine }
    .registerService<ISomeResourceAccess> { ra }
    .start()

class CallForwardingTest {

    @Test
    fun `Invoke use case`() = runTest {
        val managerProxy = proxyFactory.create<ISomeManager>()
        val response = managerProxy.forwardCall(CallRequest(listOf("a", "b", "c")))

        response.assertSuccess() shouldBe CallResponse(listOf("a", "b", "c", "resource response", "engine response", "manager response"))
    }

}
