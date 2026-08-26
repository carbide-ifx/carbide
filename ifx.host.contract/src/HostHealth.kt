package ifx.host

import kotlinx.serialization.Serializable

@Serializable
data class HostHealth(
    val state: HostState,
    val ready: Boolean,
    val live: Boolean,
    val services: List<ServiceHealthSnapshot>,
)

@Serializable
data class ServiceHealthSnapshot(
    val serviceInterface: String,
    val ready: Boolean,
    val live: Boolean,
    val detail: String? = null,
)
