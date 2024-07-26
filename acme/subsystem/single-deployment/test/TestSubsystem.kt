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
import ifx.host.RpcHost
import ifx.proxy.ProxyFactory
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe


class SubsystemTest : FreeSpec({

    RpcHost()
        .registerService<IPersonAccess> { ctx -> PersonAccess(ctx) }
        .registerService<IStaffManager> { ctx -> MembershipManager(ctx) }
        .registerService<ICustomerManager> { ctx -> MembershipManager(ctx) }
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
            val customerManager = ProxyFactory.create<ICustomerManager>()
            val request = RegisterRequest("Eric", 30)
            val response = customerManager.register(request)


        }

        "Context propagation" {
            val customerManager = ProxyFactory.create<ICustomerManager>()

            val response = customerManager.forwardContext(Empty)
        }
    }
})

