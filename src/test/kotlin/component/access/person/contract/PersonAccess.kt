package component.access.person.contract

import kotlinx.serialization.Serializable


interface PersonAccess {
    fun store(request: StorePersonRequest): StorePersonResponse
    fun filter(request: PersonCriteria): List<Person>
    fun filter(number: NumberCriteria): Number

    @Serializable
    data class NumberCriteria(val number: Int)
    @Serializable
    data class Number(val number: Int)

    @Serializable
    data class Person(val name: String, val age: Int, val fired: Boolean = false)

    @Serializable
    data class StorePersonRequest(val person: Person, val id: Int? = null)

    @Serializable
    data class StorePersonResponse(val id: Int, val person: Person)

    @Serializable
    data class PersonCriteria(
        val names: List<String> = emptyList(),
        val ages: List<Int> = emptyList(),
        val ids: List<Int> = emptyList()
    ) {
        constructor(name: String? = null, age: Int? = null, id: Int? = null) : this(
            listOfNotNull(name),
            listOfNotNull(age),
            listOfNotNull(id)
        )
        constructor(): this(emptyList(), emptyList(), emptyList())
        companion object {
            fun ofName(vararg name: String) = PersonCriteria(names = name.toList())
            fun ofAge(vararg age: Int) = PersonCriteria(ages = age.toList())
            fun ofId(vararg id: Int) = PersonCriteria(ids = id.toList())
        }
    }

}
