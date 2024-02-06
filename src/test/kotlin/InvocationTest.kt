package arve.test

import arve.host.Host
import arve.ifx.ProxyFactory
import arve.service.PersonAccessService
import arve.test.component.mannager.membership.service.MembershipManagerService
import component.access.person.contract.PersonAccess
import component.mannager.membership.contract.CustomerManager
import component.mannager.membership.contract.StaffManager
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.concurrent.thread

class InvocationTest : StringSpec({
    val port = Host.randomFreePort()
    val proxyFactory = ProxyFactory(port)
    beforeSpec {
        Host(port)
            .addService<StaffManager>(MembershipManagerService(proxyFactory))
            .addService<CustomerManager>(MembershipManagerService(proxyFactory))
            .addService<PersonAccess>(PersonAccessService())
            .start()
    }
    "A service can invoke another service" {
        val customerManager = proxyFactory.create<CustomerManager>()
        val staffManager = proxyFactory.create<StaffManager>()
        val personAccess = proxyFactory.create<PersonAccess>()
        val registered = customerManager.register(CustomerManager.RegisterRequest("John", 30))
        staffManager.fire(StaffManager.FireStaffRequest(registered.id)).success shouldBe true
        personAccess.filter(PersonAccess.PersonCriteria()).size shouldBe 1
    }
})
