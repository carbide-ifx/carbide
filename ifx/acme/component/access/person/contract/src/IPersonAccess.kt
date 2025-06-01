package acme.access.person.contract

import ifx.service.IService
import ifx.stdlib.IdGenerator
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface IPersonAccess : IService {
    suspend fun store(request: StorePersonRequest): Person
    suspend fun filter(request: PersonCriteria): List<Person>
    suspend fun echoContext(): PersonNumber
}

@Serializable
data class PersonNumber(val number: Int)

@Serializable
sealed interface Person {
    val id: String
    val name: String
    val age: Int

    @Serializable
    data class Parent(
        override val id: String,
        override val name: String,
        override val age: Int,
        val employed: Boolean = false
    ) : Person

    @Serializable
    data class Child(
        override val id: String,
        override val name: String,
        override val age: Int
    ) : Person
}

@Serializable
sealed interface StorePersonRequest {
    val id: String
    val name: String
    val age: Int

    @Serializable
    data class Parent(
        override val name: String,
        override val age: Int,
        val employed: Boolean = false,
        override val id: String = IdGenerator.generate("pa_person"),
    ) : StorePersonRequest

    @Serializable
    data class Child(
        override val name: String,
        override val age: Int,
        override val id: String = IdGenerator.generate("pa_person"),
    ) : StorePersonRequest
}

@Serializable
data class PersonCriteria(
    val ids: List<String>? = null,
    val names: List<String>? = null,
    val ages: List<Int>? = null,
    val isEmplyed: Boolean? = null
) {
    companion object {
        fun ofId(vararg id: String) = PersonCriteria(ids = id.toList())
        fun ofName(vararg name: String) = PersonCriteria(names = name.toList())
        fun ofAge(vararg age: Int) = PersonCriteria(ages = age.toList())
    }
}
