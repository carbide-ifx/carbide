@file:OptIn(ExperimentalMetadataApi::class)

package ifx.protocol.rsocket

import ifx.protocol.contract.Message
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.metadata.CompositeMetadata
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.metadata
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.io.readString


fun Message.toRequestPayload(operation: String): Payload = buildPayload {
    val meta = CompositeMetadata(
        RoutingMetadata(operation),
        IfxHeaderMetadata(header)
    )
    metadata(meta)
    data(body)
}

fun Message.toResponsePayload(): Payload = buildPayload {
    metadata(
        CompositeMetadata(
            IfxHeaderMetadata(header)
        )
    )
    data(body)
}

fun Payload.toMessage(): Message = Message(
    header = metadata.header(),
    body = data.readString()
)
