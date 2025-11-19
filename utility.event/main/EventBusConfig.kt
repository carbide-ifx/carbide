package njord.utility.event

sealed interface EventBusConfig {
    data class AzureServiceBus(val fullyQualifiedNamespace: String, val clientId: String) : EventBusConfig
    data object InMemory: EventBusConfig
}
