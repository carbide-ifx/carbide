@file:OptIn(ExperimentalMetadataApi::class)

package ifx.protocol.rsocket



import ifx.protocol.contract.Message
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.metadata
import io.rsocket.kotlin.metadata.read
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.payload.metadata
import kotlinx.io.Buffer
import kotlinx.io.readString


fun Payload.route(): String = metadata?.read(RoutingMetadata)?.tags?.firstOrNull()
    ?: error("Message Payload contains no route!")

fun Message.toRequestPayload(operation: String): Payload = buildPayload {
    metadata(RoutingMetadata(operation))
    data(this@toRequestPayload.body)
}

fun Message.toResponsePayload(): Payload = buildPayload {
    metadata(this@toResponsePayload.header)
    data(this@toResponsePayload.body)
}

fun Payload(route: String, data: Buffer = Buffer()): Payload = buildPayload {
    metadata(RoutingMetadata(route))
    data(data)
}


fun Payload.toMessage(): Message = Message(
    header = this.metadata?.readString() ?: "", body = data.readString()
)
fun Payload.toRequest(): Message = Message(
    header = this.metadata?.readString() ?: "",
    body = data.readString()
)
