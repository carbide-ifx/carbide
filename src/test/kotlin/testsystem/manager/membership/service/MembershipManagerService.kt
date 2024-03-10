package ifx.testsystem.manager.membership.service

import ifx.proxy.ProxyFactory
import ifx.service.ServiceBase
import ifx.testsystem.access.echo.contract.EchoAccess
import ifx.testsystem.access.person.contract.Dto.Person
import ifx.testsystem.access.person.contract.Dto.PersonCriteria
import ifx.testsystem.access.person.contract.Dto.StorePersonRequest
import ifx.testsystem.access.person.contract.PersonAccess
import ifx.testsystem.manager.membership.contract.CustomerManager
import ifx.testsystem.manager.membership.contract.CustomerManager.Empty
import ifx.testsystem.manager.membership.contract.CustomerManager.RegisterResponse
import ifx.testsystem.manager.membership.contract.StaffManager
import ifx.testsystem.manager.membership.contract.StaffManager.FireStaffResponse


class MembershipManagerService(private val proxyFactory: ProxyFactory) : StaffManager, CustomerManager, ServiceBase() {
    private val personAccess: PersonAccess
        get() = proxyFactory.create<PersonAccess>()

    override fun fire(request: StaffManager.FireStaffRequest): FireStaffResponse {
        val existing = personAccess.filter(PersonCriteria.ofId(request.id))
            .mapNotNull { it as? Person.Parent }
            .singleOrNull()
            ?: return FireStaffResponse(false)
        personAccess.store(StorePersonRequest(existing.copy(employed = false), request.id))
        return FireStaffResponse(true)
    }

    override fun register(request: CustomerManager.RegisterRequest): RegisterResponse {
        val stored = personAccess.store(StorePersonRequest(Person.Parent(request.name, request.age, false), null))
        return RegisterResponse(stored.id)
    }

    override fun forwardContext(e: Empty): Int {
        return proxyFactory.create<EchoAccess>(name="FromManager").echoContext(EchoAccess.EmptyEmpty).number
    }

    override suspend fun forwardContextSuspend(e: Empty): Int {
        return proxyFactory.create<EchoAccess>(name="FromManager").echoContextSuspend(EchoAccess.EmptyEmpty).number
    }
}
