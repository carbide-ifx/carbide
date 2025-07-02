package ifx.host

import ifx.host.IHost.Companion.registerService
import ifx.host.contract.IRequestResponse
import ifx.host.contract.IntPair
import ifx.host.service.RequestResponse
import ifx.logging.Log
import ifx.protocol.rsocket.RSocketEndpoint
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class LoadTest {
    val log = Log {}
    val protocol = RSocketEndpoint()
    val host = Host().addProtocol(protocol).registerService<IRequestResponse>(RequestResponse()).start()
    val myService = ProxyFactory(protocol).create<IRequestResponse>()

    @Test
    fun `Handles over 10000 requests per second`() = runTest {
        val iterations = 10000
        val duration = measureTime {
            repeat(iterations) {
                myService.add(IntPair(it, it)) shouldBe it * 2
            }
        }
        duration shouldBeLessThan 10.seconds
        log.info { "Performed 10'000 requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS" }
    }
}
