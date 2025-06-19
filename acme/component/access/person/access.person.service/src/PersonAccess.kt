package acme.access.person.service

import acme.access.person.contract.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext


class PersonAccess() : IPersonAccess {
    private val map: MutableMap<String, Person> = mutableMapOf()
    override suspend fun store(request: StorePersonRequest): Person {
        val person = request.toPerson()
        map[request.id] = person
        return person
    }

    override suspend fun filter(request: PersonCriteria): List<Person> = map
        .filter { request.ids == null || it.key in request.ids!! }
        .filter { request.names == null || it.value.name in request.names!! }
        .filter { request.ages == null || it.value.age in request.ages!! }
        .filter { request.isEmplyed == null || (it.value as? Person.Parent)?.employed == true }
        .values
        .toList()

    // Completely unrelated, just to test overloads
//    override suspend fun filter(number: NumberCriteria) = PersonNumber(number.number)

    override suspend fun echoContext(): PersonNumber {
//        println("Received context: ${currentCoroutineContext()[Context]}")
        println("Coroutine name: ${currentCoroutineContext()[CoroutineName]}")
        return PersonNumber(1)
    }
}

fun StorePersonRequest.toPerson(): Person = when (this) {
    is StorePersonRequest.Parent -> Person.Parent(id, name, age, employed)
    is StorePersonRequest.Child -> Person.Child(id, name, age)
}
