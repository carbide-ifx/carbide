package component.access.person.service

import arve.service.ServiceBase
import component.access.person.contract.PersonAccess


class PersonAccessService : PersonAccess, ServiceBase() {
    private val map: MutableMap<Int, PersonAccess.Person> = mutableMapOf()
    override fun store(request: PersonAccess.StorePersonRequest): PersonAccess.StorePersonResponse {
        val id = request.id ?: map.size
        map[id] = request.person
        return PersonAccess.StorePersonResponse(id, request.person)
    }

    override fun filter(request: PersonAccess.PersonCriteria): List<PersonAccess.Person> {
        return map
            .filter { request.ids.isEmpty() || it.key in request.ids }
            .filter { request.names.isEmpty() || it.value.name in request.names }
            .filter { request.ages.isEmpty() || it.value.age in request.ages }
            .values
            .toList()
    }

    override fun filter(number: PersonAccess.NumberCriteria) = PersonAccess.Number(number.number)
}
