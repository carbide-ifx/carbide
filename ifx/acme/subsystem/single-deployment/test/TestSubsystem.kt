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
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe


class SubsystemTest : FreeSpec({

    Host()
        .registerService<IPersonAccess> (PersonAccess())
        .registerService<IStaffManager> { ctx -> MembershipManager() }
        .registerService<ICustomerManager> { ctx -> MembershipManager() }
        .start()

    "Invocation" - {
        "Direct invocation" {
            val personAccess = ProxyFactory.create<IPersonAccess>()
            val storedJohn = personAccess.store(StorePersonRequest.Parent(name = "John", age = 30, employed = true))
            val storedPeter = personAccess.store(StorePersonRequest.Child(name = "Peter", age = 10))

            personAccess.filter(PersonCriteria.ofName("John")).single() shouldBe storedJohn
            personAccess.filter(PersonCriteria.ofName("Peter")).single() shouldBe storedPeter
        }

        "Call chain" {
            val customerManager = KrpcProxyFactory.create<ICustomerManager>()
            val request = RegisterRequest("Eric", 30)
            val response = customerManager.register(request)


        }

        "Context via proxy" {
            val customerManager = KrpcProxyFactory.create<ICustomerManager>()
            customerManager.forwardContext(Empty)
            customerManager.forwardContext(Empty)
            customerManager.forwardContext(Empty)
        }

        "!Context Propagation" {}
    }
})

