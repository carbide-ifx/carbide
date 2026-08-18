package ifx.gateway.contract

import ifx.protocol.contract.RpcFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Stable public error payload shared by gateway transports. */
@Serializable
data class GatewayFailure(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

/** Explicitly exposes a safe failure to a gateway client. Other exceptions remain internal. */
open class GatewayFailureException(
    val failure: GatewayFailure,
    cause: Throwable? = null,
) : RuntimeException(RpcFormat.encodeToString(failure), cause)
