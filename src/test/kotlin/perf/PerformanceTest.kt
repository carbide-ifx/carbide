package arve.test.perf

import arve.host.Host
import arve.ifx.ProxyFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private val log = KotlinLogging.logger { }

class PerformanceTest : StringSpec({
    val host = Host()
        .addService<Echo>(EchoService())
        .start()
    val echoService = ProxyFactory(host.port).create<Echo>()

    "Handles over 1000 requests per second" {
        val durationSeconds = 3
        var count = 0
        runBlocking {
            withTimeout(durationSeconds * 1000L) {
                while (isActive) {
                    echoService.echo(Echo.EchoRequest(count.toString())).message shouldBe count.toString()
                    count++
                }
            }
            count shouldBeGreaterThan 10_000
            log.info { "Performed $count requests in $durationSeconds seconds, for ${count / (durationSeconds)} TPS" }
        }
    }
})


