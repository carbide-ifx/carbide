package ifx.testsystem.access.person.contract

import ifx.testsystem.access.person.contract.Dto.Number
import ifx.testsystem.access.person.contract.Dto.NumberCriteria
import ifx.testsystem.access.person.contract.Dto.Person
import ifx.testsystem.access.person.contract.Dto.PersonCriteria
import ifx.testsystem.access.person.contract.Dto.StorePersonRequest
import ifx.testsystem.access.person.contract.Dto.StorePersonResponse
import kotlinx.serialization.Serializable


interface PersonAccess {
    suspend fun store(request: StorePersonRequest): StorePersonResponse
    suspend fun filter(request: PersonCriteria): List<Person>
    suspend fun filter(number: NumberCriteria): Number // Overload
}

interface Dto {
    @Serializable
    data class NumberCriteria(val number: Int)

    @Serializable
    data class Number(val number: Int)

    @Serializable
    sealed interface Person {
        val name: String
        val age: Int
    }

    @Serializable
    data class Parent(override val name: String, override val age: Int, val employed: Boolean = false) : Person

    @Serializable
    data class Child(override val name: String, override val age: Int) : Person


    @Serializable
    data class StorePersonRequest(val person: Person, val id: Int? = null)

    @Serializable
    data class StorePersonResponse(val id: Int, val person: Person)

    @Serializable
    data class PersonCriteria(
        val ids: List<Int>? = null,
        val names: List<String>? = null,
        val ages: List<Int>? = null,
        val isEmplyed: Boolean? = null
    ) {
        companion object {
            fun ofId(vararg id: Int) = PersonCriteria(ids = id.toList())
            fun ofName(vararg name: String) = PersonCriteria(names = name.toList())
            fun ofAge(vararg age: Int) = PersonCriteria(ages = age.toList())
        }
    }
}
