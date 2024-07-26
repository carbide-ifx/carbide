//package ifx
//
//import ifx.context.Context
//import ifx.service.Service
//import ifx.testsystem.access.echo.contract.EchoAccess
//import ifx.testsystem.access.echo.contract.EchoAccess.EchoRequest
//import ifx.testsystem.access.echo.contract.EchoAccess.EmptyEmpty
//import testcomponents.access.echo.service.EchoAccessService
//import ifx.testsystem.access.person.contract.Dto.Number
//import ifx.testsystem.access.person.contract.Dto.NumberCriteria
//import ifx.testsystem.access.person.contract.Dto.PersonCriteria
//import ifx.testsystem.access.person.contract.PersonAccess
//import ifx.testsystem.access.person.service.PersonAccessService
//import ifx.testsystem.manager.membership.contract.CustomerManager
//import ifx.testsystem.manager.membership.contract.CustomerManager.RegisterRequest
//import ifx.testsystem.manager.membership.service.MembershipManagerService
//import io.github.oshai.kotlinlogging.KotlinLogging
//import io.kotest.assertions.throwables.shouldThrow
//import io.kotest.core.spec.style.FreeSpec
//import io.kotest.matchers.comparables.shouldBeLessThan
//import io.kotest.matchers.shouldBe
//import kotlinx.coroutines.async
//import kotlinx.coroutines.awaitAll
//import kotlin.time.Duration.Companion.seconds
//import kotlin.time.measureTime
//
//private val log = KotlinLogging.logger { }
//
//class InvocationTest : FreeSpec({
//    val port = RpcHost.randomFreePort()
//    beforeSpec {
//        Host(port)
//            .addService(MembershipManagerService())
//            .addService(PersonAccessService())
//            .addService(EchoAccessService())
//            .start()
//    }
//
//    "Invocation" - {
//        "Direct invocation" {
//            ProxyFactory.create<EchoAccess>().echo(EchoRequest(42)).number shouldBe 42
//        }
//
//        "Overloads are supported" {
//            ProxyFactory.create<PersonAccess>().filter(PersonCriteria()).size shouldBe 0
//            ProxyFactory.create<PersonAccess>().filter(NumberCriteria(3)) shouldBe Number(3)
//        }
//
//        "A service can invoke another service" {
//            val customerManager = ProxyFactory.create<CustomerManager>()
//            val personAccess = ProxyFactory.create<PersonAccess>()
//            customerManager.register(RegisterRequest("John", 30))
//            personAccess.filter(PersonCriteria()).size shouldBe 1
//        }
//
//        "Polymorphism" {
//        }
//    }
//
//    "Exception" - {
//        "Server exception" {
//            shouldThrow<InvocationException> {
//                ProxyFactory.create<EchoAccess>().echoException(EchoRequest(42))
//            }
//        }
//        "Client exception - Serialization" {}
//        "Client exception - Server not found" {
//
//            val notfound = ProxyFactory.create<NotExistingService>()
//            shouldThrow<InvocationException> {
//                notfound.a(4)
//            }
//
//        }
//    }
//
//    "Context" - {
//
//        "Context automatically propagates through call chains" {
//            val myContext = Context(number = 42)
//            val customerManager = ProxyFactory.create<CustomerManager>(myContext)
//
//            // forwardContext calls EchoAccess.echoContext, without passing any parameters, and returns the result.
//            // EchoAccess.echoContext returns the number from the context.
//            customerManager.forwardContext(CustomerManager.Empty) shouldBe myContext.number
//        }
//        "Parallel safety: Suspend" {
//            (1..50).map { n ->
//                async {
//                    val proxyWithContext = ProxyFactory.create<EchoAccess>(Context(number = n))
//                    proxyWithContext.echoContext(EmptyEmpty).number shouldBe n
//                }
//            }.awaitAll()
//        }
//    }
//
//    "!Performance" - {
//        val iterations = 10_000
//        val echoService = ProxyFactory(port).create<EchoAccess>()
//        "Handles over 1000 requests per second" {
//            val duration = measureTime {
//                repeat(iterations) {
//                    echoService.echo(EchoRequest(it)).number shouldBe it
//                }
//            }
//            duration shouldBeLessThan 10.seconds
//            log.info { "Performed 10'000 requests in $duration seconds, for ${iterations * 1000 / (duration.inWholeMilliseconds)} TPS" }
//        }
//    }
//})
//
//
//interface NotExistingService: Service {
//    suspend fun a(i: Int) = i
//}
