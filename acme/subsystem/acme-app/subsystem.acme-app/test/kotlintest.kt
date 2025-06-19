package acme.subsystem.singledeployment


import acme.access.person.contract.IPersonAccess
import acme.access.person.contract.PersonCriteria
import acme.access.person.contract.StorePersonRequest
import acme.access.person.service.PersonAccess
import acme.manager.membership.contract.Empty
import acme.manager.membership.contract.ICustomerManager
import acme.manager.membership.contract.IStaffManager
import acme.manager.membership.contract.RegisterRequest
import acme.manager.membership.service.MembershipManager
import ifx.host.Host
import ifx.host.registerService
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.ProxyFactory
import io.kotest.common.runBlocking
import kotlin.test.Test

class KSystemTest {
    val host = Host()
        .addProtocol(RSocketProtocol())
        .registerService<IPersonAccess>(PersonAccess())
        .registerService<IStaffManager>(MembershipManager())
        .registerService<ICustomerManager>(MembershipManager())
        .start()

    @Test
    fun theTest() = runBlocking {

        val personAccess = _root_ide_package_.ifx.proxy.ProxyFactory().create<IPersonAccess>()
        val storedJohn = personAccess.store(StorePersonRequest.Parent(name = "John", age = 30, employed = true))
        val storedPeter = personAccess.store(StorePersonRequest.Child(name = "Peter", age = 10))
        personAccess.filter(PersonCriteria.ofName("John")).single().let { println(it) }
        personAccess.filter(PersonCriteria.ofName("Peter")).single().let { println(it) }

        val customerManager = ProxyFactory().create<ICustomerManager>()
        println(customerManager.register(RegisterRequest("Eric", 30)))
        println(customerManager.forwardContext(Empty))
        println(customerManager.forwardContext(Empty))
        println(customerManager.forwardContext(Empty))

    }
}


