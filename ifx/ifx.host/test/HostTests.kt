package ifx.host
//
//import acme.access.person.contract.IPersonAccess
//import acme.access.person.contract.PersonCriteria
//import acme.access.person.contract.StorePersonRequest
//import acme.access.person.service.PersonAccess
//import acme.manager.membership.contract.Empty
//import acme.manager.membership.contract.ICustomerManager
//import acme.manager.membership.contract.IStaffManager
//import acme.manager.membership.contract.RegisterRequest
//import acme.manager.membership.service.MembershipManager
//import ifx.context.Context
//import ifx.host.IHost.Companion.registerService
//import ifx.protocol.rsocket.RSocketEndpoint
//import ifx.proxy.ProxyFactory
//import io.kotest.matchers.shouldBe
//import kotlinx.coroutines.test.runTest
//import kotlin.test.Test
//
//class AcmeTest {
//    @Test
//    fun theTest() = runTest {
//        val personAccess = proxyFactory.create<IPersonAccess>()
//        val storedJohn = personAccess.store(StorePersonRequest.Parent(name = "John", age = 30, employed = true))
//        val storedPeter = personAccess.store(StorePersonRequest.Child(name = "Peter", age = 10))
//        personAccess.filter(PersonCriteria.ofName("John")).single().let { println(it) }
//        personAccess.filter(PersonCriteria.ofName("Peter")).single().let { println(it) }
//
//        val customerManager = proxyFactory.create<ICustomerManager>()
//        println(customerManager.register(RegisterRequest("Eric", 30)))
//        println(customerManager.forwardContext(Empty))
//    }
//
//    @Test
//    fun `Context automatically propagates through call chains`() = runTest {
//        val myContext = Context(number = 42)
//        val customerManager = proxyFactory.create<ICustomerManager>()
//
//        // forwardContext calls EchoAccess.echoContext, without passing any parameters, and returns the result.
//        // EchoAccess.echoContext returns the number from the context.
//        customerManager.forwardContext(Empty) shouldBe myContext.number
//    }
//
//    @Test
//    fun `A service can invoke another service`(): Unit = runTest {
//        val customerManager = proxyFactory.create<ICustomerManager>()
//        val personAccess = proxyFactory.create<IPersonAccess>()
//        customerManager.register(RegisterRequest("John", 30))
//        personAccess.filter(PersonCriteria()).size shouldBe 1
//    }
//
//    companion object {
//        val protocol = RSocketEndpoint()
//        val proxyFactory = ProxyFactory(protocol)
//        val host = Host()
//            .addProtocol(protocol)
//            .registerService<IPersonAccess>(PersonAccess())
//            .registerService<IStaffManager>(MembershipManager())
//            .registerService<ICustomerManager>(MembershipManager())
//            .start()
//    }
//}
