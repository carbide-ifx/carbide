package acme.manager.membership.service

import acme.access.person.contract.Person
import acme.access.person.contract.StorePersonRequest

fun Person.toStoreRequest(): StorePersonRequest = when (this) {
    is Person.Parent -> StorePersonRequest.Parent(id = id, name = name, age= age, employed = employed)
    is Person.Child -> StorePersonRequest.Child(id = id, name = name, age = age)
}
