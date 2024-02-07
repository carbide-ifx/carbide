package arve.test.component.mannager.membership.service

import arve.ifx.ProxyFactory
import arve.service.ServiceBase
import component.access.person.contract.PersonAccess
import component.mannager.membership.contract.CustomerManager
import component.mannager.membership.contract.StaffManager


class MembershipManagerService(private val proxyFactory: ProxyFactory) : StaffManager, CustomerManager, ServiceBase() {
    private val personAccess: PersonAccess
        get() = proxyFactory.create<PersonAccess>()

    override fun fire(request: StaffManager.FireStaffRequest): StaffManager.FireStaffResponse {
        val existing = personAccess.filter(PersonAccess.PersonCriteria.ofId(request.id)).singleOrNull()
            ?: return StaffManager.FireStaffResponse(false)
        personAccess.store(PersonAccess.StorePersonRequest(existing.copy(fired = true), request.id))
        return StaffManager.FireStaffResponse(true)
    }

    override fun register(request: CustomerManager.RegisterRequest): CustomerManager.RegisterResponse {
        val stored = personAccess.store(PersonAccess.StorePersonRequest(PersonAccess.Person(request.name, request.age, false), null))
        return CustomerManager.RegisterResponse(stored.id)
    }
}
