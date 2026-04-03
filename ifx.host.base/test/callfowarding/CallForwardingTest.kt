package ifx.host.callfowarding

import ifx.context.Context
import ifx.host.HostBase
import ifx.host.IHost.Companion.registerService
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactoryBase
import ifx.test.assertSuccess
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test


class CallForwardingTest() {

    val protocol = RSocketProtocol()
    val proxyFactory = ProxyFactoryBase(protocol)
    val manager = SomeManager(proxyFactory)
    val engine = SomeEngine(proxyFactory)
    val ra = SomeResourceAccess()
    val host = HostBase(protocol)
        .registerService<ISomeManager> { manager }
        .registerService<ISomeEngine> { engine }
        .registerService<ISomeResourceAccess> { ra }
        .open()


    @Test
    fun `Invoke use case`() = runTest {
        val managerProxy = proxyFactory.create<ISomeManager>()
        val a = List(1000) { i ->
            withContext(Context("$i")) {
                i to managerProxy.someUseCase(CallRequest(listOf("a", "b", "c"))).assertSuccess()
            }
        }
        a.forAll { (i, response) ->
            val expected =
                listOf("a", "b", "c", "$i", "resource response", "engine response", "manager response");
            response shouldBe CallResponse(expected)
        }
    }

    @Test
    fun `Blocking use case`() = runTest {
        val managerProxy = proxyFactory.create<ISomeManager>()
        coroutineScope {
            val results = List(1000) { i ->
                async {
                    ScopedValue.where(Context.CTX, Context("$i")).call<Pair<Int, CallResponse>, Exception> {
                        val response =
                            managerProxy.someBlockingUseCase(CallRequest(listOf("a", "b", "c"))).assertSuccess()
                        val expected =
                            listOf("a", "b", "c", "$i", "resource response", "engine response", "manager response");
                        response shouldBe CallResponse(expected)
                        i to response
                    }
                }
            }.awaitAll()
            results.forAll { (i, response) ->
                val expected =
                    listOf("a", "b", "c", "$i", "resource response", "engine response", "manager response");
                response shouldBe CallResponse(expected)
            }
        }
    }
}

