package acme.subsystem.singledeployment


import acme.access.person.contract.IPersonAccess
import acme.access.person.service.PersonAccess
import acme.manager.membership.contract.ICustomerManager
import acme.manager.membership.contract.IStaffManager
import acme.manager.membership.service.MembershipManager
import ifx.host.Host
import ifx.host.registerService
import ifx.protocol.rsocket.RSocketProtocol
import ifx.proxy.ProxyFactory
import io.kotest.core.spec.style.FreeSpec


class SubsystemTest : FreeSpec({
    Host()
        .addProtocol(RSocketProtocol())
        .registerService<IPersonAccess>(PersonAccess())
        .registerService<IStaffManager>(MembershipManager())
        .registerService<ICustomerManager>(MembershipManager())
        .start()
    ProxyFactory().create<ICustomerManager>()
    "Invocation" - {
//        "Direct invocation" {
//            val personAccess = ProxyFactory().create<IPersonAccess>()
//            val storedJohn = personAccess.store(StorePersonRequest.Parent(name = "John", age = 30, employed = true))
//            val storedPeter = personAccess.store(StorePersonRequest.Child(name = "Peter", age = 10))
//
//            personAccess.filter(PersonCriteria.ofName("John")).single() shouldBe storedJohn
//            personAccess.filter(PersonCriteria.ofName("Peter")).single() shouldBe storedPeter
//        }
//
//        "Call chain" {
//            val customerManager = ProxyFactory().create<ICustomerManager>()
//            val request = RegisterRequest("Eric", 30)
//            val response = customerManager.register(request)
//        }
//
//        "Context via proxy" {
//            val customerManager = ProxyFactory().create<ICustomerManager>()
//            customerManager.forwardContext(Empty)
//            customerManager.forwardContext(Empty)
//            customerManager.forwardContext(Empty)
//        }

        "!Context Propagation" {}
    }
})

