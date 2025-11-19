package njord.utility.event.implementations

import com.azure.core.amqp.exception.AmqpErrorCondition
import com.azure.core.amqp.exception.AmqpException
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusErrorContext
import com.azure.messaging.servicebus.ServiceBusException
import com.azure.messaging.servicebus.ServiceBusFailureReason
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext
import com.azure.messaging.servicebus.ServiceBusSenderClient
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions
import com.azure.messaging.servicebus.administration.models.CreateTopicOptions
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import njord.utility.context.AmbientContext
import njord.utility.context.AuditContext
import njord.utility.context.Context
import njord.utility.event.Event
import njord.utility.event.EventBus
import njord.utility.event.topicNameFor
import reactor.core.publisher.Hooks
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

typealias MessageProcessor = (ServiceBusReceivedMessageContext) -> Unit
typealias ErrorProcessor = (ServiceBusErrorContext) -> Unit

class AzureServiceBus(
    private val fullyQualifiedNamespace: String, // "{your-namespace}.servicebus.windows.net"
    private val clientId: String
) : EventBus {

    init {
        configureReactorErrorHandling()
    }

    val credential
        get() = DefaultAzureCredentialBuilder()
            .managedIdentityClientId(clientId)
            .workloadIdentityClientId(clientId)
            .build()

    private val senderClients = mutableMapOf<String, ServiceBusSenderClient>()
    private val log = KotlinLogging.logger { }

    private val adminClient = ServiceBusAdministrationClientBuilder()
        .credential(fullyQualifiedNamespace, credential)
        .buildClient()

    override fun <T : Event> publish(event: T, topicName: String) {
        val senderClient = senderClients.getOrPut(topicNameFor(event)) {
            ServiceBusClientBuilder()
                .credential(fullyQualifiedNamespace, credential)
                .sender()
                .topicName(topicNameFor(event))
                .buildClient()
        }
        val send = { senderClient.sendMessage(event.toServiceBusMessage()) }
        try {
            log.info { "Publishing event to topic $topicName: $event" }
            send()
        } catch (e: ServiceBusException) {
            if (e.reason == ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND) {
                log.info { "Attempting to publish to topic that does not exist. Creating topic ${topicNameFor(event)}" }
                adminClient.createTopic(topicNameFor(event), TOPIC_OPTIONS)
                return send()
            }
            log.error { "Exception when publishing message to topic $topicName: ${e.message}" }
            throw e
        }
    }

    fun configureReactorErrorHandling() {
        Hooks.onErrorDropped { e ->
            // Helper: unwrap causes to find if any matches our filter
            fun Throwable?.findAmqpIdleTimeoutCause(): AmqpException? {
                var current = this
                while (current != null) {
                    if (current is AmqpException &&
                        current.errorCondition == AmqpErrorCondition.CONNECTION_FORCED &&
                        current.message?.contains("did not have any active links") == true
                    ) {
                        return current
                    }
                    current = current.cause
                }
                return null
            }

            val amqpIdleTimeoutEx = e.findAmqpIdleTimeoutCause()
            if (amqpIdleTimeoutEx != null) {
                log.debug { "Ignored idle connection close from Service Bus: ${e.message}" }
                return@onErrorDropped
            }

            // Not matched: log as error
            log.error(e) { "Unhandled dropped error in Reactor" }
        }
    }

    override fun <T : Event> subscribe(
        topicName: String,
        subscriptionName: String,
        serializer: KSerializer<T>,
        eventHandler: (T) -> Unit
    ) {
        val processor: MessageProcessor = { context ->
            val body = context.message.body.toString()
            runCatching {
                val event = Json.decodeFromString(serializer, body)
                val ctx = AmbientContext(traceId = "Event-${Uuid.random()}", audit = AuditContext.System("AzureServiceBus"))
                ScopedValue.where(Context.ambientContext, ctx).run {
                    log.info { "$topicName($subscriptionName) - Handling event $event (${ctx.traceId})"  }
                    eventHandler(event)
                    context.complete()
                }
            }.onFailure { e ->
                log.error(e) {
                    "$topicName($subscriptionName) - Exception when handling event ${body}. " +
                            "Event will be retried or go to DLQ"
                }
                // Put message back on the queue. Messages are retried 10 times before being moved to DeadLetterQueue
                context.abandon()
            }
        }
        if (!adminClient.getTopicExists(topicName)) {
            adminClient.createTopic(topicName, TOPIC_OPTIONS)
        }
        if (!adminClient.getSubscriptionExists(topicName, subscriptionName)) {
            adminClient.createSubscription(topicName, subscriptionName, SUBSCRIPTION_OPTIONS)
        }
        processorClient(topicName, subscriptionName, processor).start()
        log.info { "Subscribing to $topicName($subscriptionName)" }
    }


    private fun processorClient(
        topicName: String,
        subscriptionName: String,
        messageProcessor: (ServiceBusReceivedMessageContext) -> Unit,
        errorProcessor: (ServiceBusErrorContext) -> Unit = defaultErrorHandler,
    ) = ServiceBusClientBuilder()
        .credential(fullyQualifiedNamespace, credential)
        .processor()
        .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
        .disableAutoComplete() // Make sure to explicitly opt in to manual settlement (e.g. complete, abandon).
        .disableAutoComplete()
        .topicName(topicName)
        .subscriptionName(subscriptionName)
        .processMessage(messageProcessor)
        .processError(errorProcessor)
        .buildProcessorClient()

    private val defaultErrorHandler: (ServiceBusErrorContext) -> Unit = { errorContext: ServiceBusErrorContext ->
        val error = when (val ex = errorContext.exception) {
            is ServiceBusException -> "Error source: ${errorContext.errorSource}, reason: ${ex.reason}"
            else -> "Error occurred: $ex"
        }
        log.error { error }
    }

    private fun <T : Event> T.toServiceBusMessage() =
        ServiceBusMessage(Json.encodeToString(serializer(this::class.java), this))

    companion object {
        val TOPIC_OPTIONS = CreateTopicOptions()
            .setAutoDeleteOnIdle(30.days.toJavaDuration())
            .setDefaultMessageTimeToLive(7.days.toJavaDuration())
            .setOrderingSupported(true)


        val SUBSCRIPTION_OPTIONS = CreateSubscriptionOptions()
            .setAutoDeleteOnIdle(30.days.toJavaDuration())
            .setDefaultMessageTimeToLive(7.days.toJavaDuration())
            .setDeadLetteringOnMessageExpiration(true)
    }
}
