package ifx.host

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HostState {
    @SerialName("new")
    NEW,

    @SerialName("starting")
    STARTING,

    @SerialName("ready")
    READY,

    @SerialName("draining")
    DRAINING,

    @SerialName("stopping")
    STOPPING,

    @SerialName("stopped")
    STOPPED,

    @SerialName("failed")
    FAILED,
}
