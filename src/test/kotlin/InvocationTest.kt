package ifx

import ifx.ctx.Context
import ifx.host.Host
import ifx.proxy.ProxyFactory
import ifx.proxy.WrappedTestException
import ifx.testsystem.access.echo.contract.EchoAccess
import ifx.testsystem.access.echo.contract.EchoAccess.EchoRequest
import ifx.testsystem.access.echo.contract.EchoAccess.EmptyEmpty
import ifx.testsystem.access.echo.service.EchoAccessService
import ifx.testsystem.access.person.contract.Dto
import ifx.testsystem.access.person.contract.Dto.Number
import ifx.testsystem.access.person.contract.Dto.NumberCriteria
import ifx.testsystem.access.person.contract.Dto.Person
import ifx.testsystem.access.person.contract.Dto.PersonCriteria
import ifx.testsystem.access.person.contract.Dto.StorePersonRequest
import ifx.testsystem.access.person.contract.PersonAccess
import ifx.testsystem.access.person.service.PersonAccessService
import ifx.testsystem.manager.membership.contract.CustomerManager
import ifx.testsystem.manager.membership.contract.CustomerManager.RegisterRequest
import ifx.testsystem.manager.membership.service.MembershipManagerService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private val log = KotlinLogging.logger { }

class InvocationTest : FreeSpec({
    val port = Host.randomFreePort()
    val proxyFactory = ProxyFactory(port)
    beforeSpec {
        Host(port)
            .addService(MembershipManagerService(proxyFactory))
            .addService(PersonAccessService())
            .addService(EchoAccessService())
            .start()
    }
    val echoAccess = proxyFactory.create<EchoAccess>()

    "Invocation" - {
        "Blocking invocation" {
            echoAccess.echo(EchoRequest(42)).number shouldBe 42
        }

        "Suspending invocation" {
            echoAccess.echoSuspend(EchoRequest(42)).number shouldBe 42
        }

        "Overloads are supported" {
            proxyFactory.create<PersonAccess>().filter(PersonCriteria()).size shouldBe 0
            proxyFactory.create<PersonAccess>().filter(NumberCriteria(3)) shouldBe Number(3)
        }

        "A service can invoke another service" {
            val customerManager = proxyFactory.create<CustomerManager>()
//            val personAccess = proxyFactory.create<PersonAccess>()
            customerManager.register(RegisterRequest("John", 30))
//            personAccess.filter(PersonCriteria()).size shouldBe 1
        }

        "Polymorphism" {
        }
    }

    "Exception" - {
        "Suspending exception" {
            shouldThrow<WrappedTestException> {
                echoAccess.echoExceptionSuspend(EchoRequest(42))
            }
        }
        "Blocking exception" {
            shouldThrow<WrappedTestException> {
                echoAccess.echoException(EchoRequest(42))
            }
        }
    }

    "Context" - {
        val myContext = Context(number = 42)
        val customerManager = proxyFactory.create<CustomerManager>(myContext)
        "Context automatically propagates through blocking call chains" {
            // forwardContext calls EchoAccess.echoContext, without passing any parameters, and returns the result.
            // EchoAccess.echoContext returns the number from the context.
            customerManager.forwardContext(CustomerManager.Empty) shouldBe myContext.number
        }
        "Context automatically propagates through suspending call chains" {
            // forwardContextSuspend calls EchoAccess.echoContextSuspend, without passing any parameters, and returns the result.
            // EchoAccess.echoContext returns the number from the context.
            customerManager.forwardContextSuspend(CustomerManager.Empty) shouldBe myContext.number
        }
        "Parallel safety: Suspend" {
            (1..50).map { n ->
                async {
                    val echoAccess = proxyFactory.create<EchoAccess>(Context(number = n))
                    echoAccess.echoContextSuspend(EmptyEmpty).number shouldBe n
                }
            }.awaitAll()
        }
        "Parallel safety: Blocking" {
            (1..50).map { n ->
                async {
                    val echoAccess = proxyFactory.create<EchoAccess>(Context(number = n))
                    echoAccess.echoContext(EmptyEmpty).number shouldBe n
                }
            }.awaitAll()
        }
    }

    "!Performance" - {
        val iterations = 10_000
        val echoService = ProxyFactory(port).create<EchoAccess>()
        "Handles over 1000 blocking requests per second" {
            val duration = measureTime {
                repeat(iterations) {
                    echoService.echo(EchoRequest(it)).number shouldBe it
                }
            }
            duration shouldBeLessThan 10.seconds
            log.info { "Performed 10'000 requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS" }
        }
        "Handles over 1000 suspending requests per second" {
            val duration = measureTime {
                repeat(iterations) {
                    echoService.echoSuspend(EchoRequest(it)).number shouldBe it
                }
            }
            duration shouldBeLessThan 10.seconds
            log.info { "Performed 10'000 requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS" }
        }
    }
})


