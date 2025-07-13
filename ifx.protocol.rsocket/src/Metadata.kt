@file:OptIn(ExperimentalMetadataApi::class)

package ifx.protocol.rsocket


import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.core.CustomMimeType
import io.rsocket.kotlin.metadata.CompositeMetadata
import io.rsocket.kotlin.metadata.Metadata
import io.rsocket.kotlin.metadata.RawMetadata
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.hasMimeTypeOf
import io.rsocket.kotlin.metadata.read
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString

class IfxHeaderMetadata(val header: String) : Metadata by RawMetadata(
    mimeType = mimeType,
    content = Buffer().apply { writeString(header) }
) {
    companion object {
        val mimeType = CustomMimeType(text = "application/x-ifx-header")
    }
}

fun Buffer?.header(): String = this?.read(CompositeMetadata)?.entries
    ?.singleOrNull() { it.mimeType == IfxHeaderMetadata.mimeType }
    ?.content
    ?.readString()
    ?: ""

fun Buffer?.route(): String {
    val copy = Buffer()
    this?.copyTo(copy)
    return copy.read(CompositeMetadata)
        .entries
        .singleOrNull { it.hasMimeTypeOf(RoutingMetadata) }?.content?.read(RoutingMetadata)
        ?.tags
        ?.firstOrNull()
        ?: error("Message Payload contains no route!")
}


