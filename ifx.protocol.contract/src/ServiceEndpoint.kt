package ifx.protocol.contract

import kotlinx.serialization.Serializable

/** Network destination of a remotely hosted group of IFX services. */
@Serializable
data class ServiceEndpoint(
    val host: String,
    val port: Int,
) {
    init {
        require(host.isNotBlank()) { "Service endpoint host must not be blank" }
        require(port in 1..65535) { "Service endpoint port must be between 1 and 65535" }
    }
}
