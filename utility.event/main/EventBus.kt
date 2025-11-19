package njord.utility.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface Event // Marker interface for event DTOs

interface EventBus {

    /**
     * Publish event, explicitly provide topicName
     */
    fun <T : Event> publish(event: T, topicName: String)

    /**
     * Publish an event to the event bus Topic name is derived from the event Type
     */
    fun <T : Event> publish(event: T) = publish(event, topicNameFor(event))

    /**
     * Subscribe to an event topic. `subscriptionName` is used to identify the subscription,
     * and should typically correspond to the subscribing service's name
     */
    fun <T : Event> subscribe(
        topicName: String,
        subscriptionName: String,
        serializer: KSerializer<T>,
        eventHandler: (T) -> Unit
    )
}

/**
 * Subscribe to an event topic. The topic name is derived from the event Type. `subscriptionName` is used to identify
 * the subscription, and should typically correspond to the subscribing service's name
 */

inline fun <reified T : Event> EventBus.subscribe(subscriptionName: String, noinline handler: (T) -> Unit) =
    subscribe(topicNameFor<T>(), subscriptionName, serializer<T>(), handler)


