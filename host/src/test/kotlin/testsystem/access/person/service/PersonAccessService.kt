package ifx.testsystem.access.person.service

import ifx.service.ServiceBase
import ifx.testsystem.access.person.contract.Dto.Number
import ifx.testsystem.access.person.contract.Dto.NumberCriteria
import ifx.testsystem.access.person.contract.Dto.Parent
import ifx.testsystem.access.person.contract.Dto.Person
import ifx.testsystem.access.person.contract.Dto.PersonCriteria
import ifx.testsystem.access.person.contract.Dto.StorePersonRequest
import ifx.testsystem.access.person.contract.Dto.StorePersonResponse
import ifx.testsystem.access.person.contract.PersonAccess


class PersonAccessService : PersonAccess, ServiceBase() {
    private val map: MutableMap<Int, Person> = mutableMapOf()
    override suspend fun store(request: StorePersonRequest): StorePersonResponse {
        val id = request.id ?: map.size
        map[id] = request.person
        return StorePersonResponse(id, request.person)
    }

    override suspend fun filter(request: PersonCriteria): List<Person> = map
        .filter { request.ids == null || it.key in request.ids }
        .filter { request.names == null || it.value.name in request.names }
        .filter { request.ages == null || it.value.age in request.ages }
        .filter { request.isEmplyed == null || (it.value as? Parent)?.employed == true }
        .values
        .toList()

    override suspend fun filter(number: NumberCriteria) = Number(number.number) // Completely unrelated, just to test overloads
}
