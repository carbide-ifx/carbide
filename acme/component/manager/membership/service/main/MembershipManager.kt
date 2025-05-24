package acme.manager.membership.service

import acme.access.person.contract.IPersonAccess
import acme.access.person.contract.Person
import acme.access.person.contract.PersonCriteria
import acme.access.person.contract.StorePersonRequest
import acme.manager.membership.contract.*
import ifx.proxy.KrpcProxyFactory
import ifx.service.Response
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


class MembershipManager(override val coroutineContext: CoroutineContext) : IStaffManager, ICustomerManager {

    override suspend fun fire(request: FireStaffRequest): Response<FireStaffResponse> {

        val personAccess = KrpcProxyFactory.create<IPersonAccess>()
        val existing = personAccess
            .filter(PersonCriteria.ofId(request.id))
            .mapNotNull { it as? Person.Parent }
            .singleOrNull()
            ?: return Response.fail(MembershipError.StaffNotFound)
        personAccess.store(existing.copy(employed = false).toStoreRequest())
        return Response(FireStaffResponse(true))
    }

    override suspend fun register(request: RegisterRequest): Response<RegisterResponse> {
        val personAccess = KrpcProxyFactory.create<IPersonAccess>()
        val stored = personAccess.store(StorePersonRequest.Parent(request.name, request.age, false))
        return Response(RegisterResponse(stored.id))
    }

    override suspend fun forwardContext(e: Empty): Int = withContext (coroutineContext) {
        KrpcProxyFactory.create<IPersonAccess>().echoContext().number
    }
}

