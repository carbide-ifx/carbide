package njord.utility.event

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.freeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import njord.utility.context.AmbientContext
import njord.utility.context.AuditContext
import njord.utility.context.Context
import njord.utility.event.implementations.AzureServiceBus
import njord.utility.event.implementations.InMemoryBus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid


// Set to true to include run against AzureServiceBus staging namespace.
// Your local machine must be authenticated with Azure CLI and have access to the AzureServiceBus.
private const val ENABLE_ASB_TEST: Boolean = false
private const val STAGING_BUS_NAMESPACE = "njord-staging.servicebus.windows.net"
private const val ENTRA_SWE_GROUP_ID = "53711e40-3f89-40f0-9289-1c66bc85596e"

class EventBusTest : FreeSpec({
    "Topic name" - {
        "from type" { topicNameFor<TestEvents.OnUserCreated>() shouldBe "TestEvents.OnUserCreated" }
        "from value" { topicNameFor(TestEvents.OnUserCreated("name", "email")) shouldBe "TestEvents.OnUserCreated" }
    }
    include("InMemory", eventBusTest(InMemoryBus()))
    if (ENABLE_ASB_TEST) {
        include("AzureServiceBus", eventBusTest(AzureServiceBus(STAGING_BUS_NAMESPACE, ENTRA_SWE_GROUP_ID)))
    }
})

fun eventBusTest(eventBus: EventBus, numberOfEvents: Int = 25, timeout: Duration = 10.seconds) = freeSpec {
    val receivedUserEvents = mutableListOf<Pair<TestEvents.OnUserCreated, AmbientContext>>()
    val receivedDepartureEvents = mutableListOf<Pair<TestEvents.OnDeparture, AmbientContext>>()

    eventBus.subscribe<TestEvents.OnUserCreated>("receivingManager") { event ->
        val ctx = Context.getBlocking()
        receivedUserEvents.add(event to ctx)
    }
    eventBus.subscribe<TestEvents.OnDeparture>("receivingManager") { event ->
        val ctx = Context.getBlocking()
        receivedDepartureEvents.add(event to ctx)
    }
    "Emit Events" {
        repeat(numberOfEvents) {
            eventBus.publish(TestEvents.OnUserCreated("Testus", "testus@test.com"))
            eventBus.publish(TestEvents.OnDeparture(Uuid.random().toString()))
        }
    }

    "Events were received, with correct context" {
        eventually(timeout) {
            receivedUserEvents.size shouldBe numberOfEvents
            receivedDepartureEvents.size shouldBe numberOfEvents

            receivedUserEvents.map { it.second.traceId }.shouldBeUnique()
            receivedUserEvents.map { it.second }
                .forAll { it.audit shouldBe AuditContext.System(eventBus::class.java.simpleName) }
        }
    }
}


interface TestEvents : Event {
    @Serializable
    data class OnUserCreated(val name: String, val email: String) : Event

    @Serializable
    data class OnDeparture(val departureId: String) : Event
}
