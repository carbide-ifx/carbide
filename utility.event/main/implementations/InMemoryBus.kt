package njord.utility.event.implementations

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import njord.utility.context.AmbientContext
import njord.utility.context.AuditContext
import njord.utility.context.Context
import njord.utility.event.Event
import njord.utility.event.EventBus
import kotlin.uuid.Uuid

class InMemoryBus : EventBus {
    private val log = KotlinLogging.logger { }
    val topics: MutableMap<String, MutableSet<(String) -> Unit>> = mutableMapOf()

    override fun <T : Event> publish(event: T, topicName: String) {
        log.info { "[Event Bus] Event published to topic $topicName: $event" }
        val json = Json.encodeToString(serializer(event::class.java), event)
        topics[topicName]?.forEach { handler ->
            val ctx = AmbientContext(traceId = "Event-${Uuid.random()}", audit = AuditContext.System("InMemoryBus"))
            ScopedValue.where(Context.ambientContext, ctx).run {
                handler(json)
            }
        }
    }

    override fun <T : Event> subscribe(
        topicName: String,
        subscriptionName: String,
        serializer: KSerializer<T>,
        eventHandler: (T) -> Unit
    ) {
        val handlersForTopic = topics.computeIfAbsent(topicName) { mutableSetOf() }
        val serializedHandler = { eventString: String ->
            log.info { "[Event Bus] Event sent from topic $topicName to subscription $subscriptionName: $eventString" }
            val event = Json.decodeFromString(serializer, eventString)
            eventHandler(event)
        }
        handlersForTopic.add(serializedHandler)
        log.info { "[Event Bus] Subscription created on topic $topicName: $subscriptionName" }
    }
}
