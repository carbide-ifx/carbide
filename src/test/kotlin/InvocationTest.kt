package arve.test

import arve.host.Host
import arve.ifx.ProxyFactory
import component.access.person.contract.PersonAccess
import component.access.person.service.PersonAccessService
import component.access.polymorphism.contract.PolymorphicAccess
import component.access.polymorphism.service.JavaEchoService
import component.manager.membership.contract.CustomerManager
import component.manager.membership.contract.StaffManager
import component.manager.membership.service.MembershipManagerService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InvocationTest : StringSpec({
    val port = Host.randomFreePort()
    val proxyFactory = ProxyFactory(port)
    beforeSpec {
        Host(port)
            .addService(MembershipManagerService(proxyFactory))
            .addService<PersonAccess>(PersonAccessService())
            .addService<PolymorphicAccess>(JavaEchoService())
            .start()
    }
    "A service can invoke another service" {
        val customerManager = proxyFactory.create<CustomerManager>()
        val registered = customerManager.register(CustomerManager.RegisterRequest("John", 30))
        proxyFactory.create<StaffManager>().fire(StaffManager.FireStaffRequest(registered.id)).success shouldBe true
        proxyFactory.create<PersonAccess>().filter(PersonAccess.PersonCriteria()).size shouldBe 1
    }

    "Overloads are supported" {
        proxyFactory.create<PersonAccess>().filter(PersonAccess.NumberCriteria(3)) shouldBe PersonAccess.Number(3)
    }

    "Polymorphism" {
        proxyFactory.create<PolymorphicAccess>().echo(
            PolymorphicAccess.RecordRequest.Please(3)
        ) shouldBe PolymorphicAccess.RecordResponse.Yes(3)
        proxyFactory.create<PolymorphicAccess>().echo(
            PolymorphicAccess.RecordRequest.Thanks("Hello")
        ) shouldBe PolymorphicAccess.RecordResponse.No("Hello")
    }

    "Context passing" {

    }
})
