package ifx.host.loadtest

import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.host.contract.IRequestResponse
import ifx.host.contract.IntPair
import ifx.host.service.RequestResponse
import ifx.logging.Log
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class LoadTest {
    val log = Log {}
    val protocol = RSocketProtocol()
    val host = Host(protocol)
        .registerService<IRequestResponse>(RequestResponse())
        .registerService<ILoadTestService>(LoadTestService())
        .open()

    @Test
    fun `Request Throughput`() = runTest {
        val requestResponse = ProxyFactory(protocol).create<IRequestResponse>()
        val iterations = 30000
        val duration = measureTime {
            coroutineScope {
                repeat(iterations) {
                    launch {
                        requestResponse.add(IntPair(it, it)) shouldBe it * 2
                    }
                }
            }
        }
        duration shouldBeLessThan 10.seconds
        log.info { "Performed $iterations separate requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS" }
    }

    @Test
    fun `30k Departures - flow`() = runTest {
        val loadTestService = ProxyFactory(protocol).create<ILoadTestService>()
        var count = 0
        val duration = measureTime {
            loadTestService.loadDepartures().collect { _ -> count++ }
        }
        log.info { "Flow: $count departures in $duration seconds, for ${count * 1000 / (duration.inWholeMilliseconds)} DPS" }
    }


    @Test
    fun `1k Timetables - flow`() = runTest {
        val loadTestService = ProxyFactory(protocol).create<ILoadTestService>()
        var count = 0
        val duration = measureTime {
            loadTestService.loadTimeTables().collect { _ -> count++ }
        }
        log.info { "Flow: $count timetables in $duration seconds, for ${count * 1000 / (duration.inWholeMilliseconds)} TPS" }
    }

    @Test
    fun `30k Departures - list`() = runTest {
        val loadTestService = ProxyFactory(protocol).create<ILoadTestService>()
        var count = 0
        val duration = measureTime {
            loadTestService.loadDeparturesList().map { _ -> count++ }
        }
        log.info { "List: $count departures in $duration seconds, for ${count * 1000 / (duration.inWholeMilliseconds)} DPS" }
    }


    @Test
    fun `1k Timetables - list`() = runTest {
        val loadTestService = ProxyFactory(protocol).create<ILoadTestService>()
        var count = 0
        val duration = measureTime {
            loadTestService.loadTimeTablesList().map { _ -> count++ }
        }
        log.info { "List: $count timetables in $duration seconds, for ${count * 1000 / (duration.inWholeMilliseconds)} TPS" }
    }
}
