@file:OptIn(ExperimentalMetadataApi::class)

package ifx.rsocket

import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.metadata
import io.rsocket.kotlin.metadata.read
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.io.Buffer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType


val format: StringFormat = Json { encodeDefaults = true }

fun Payload.route(): String = metadata?.read(RoutingMetadata)?.tags?.firstOrNull()
    ?: error("Request Payload contains no route!")


inline fun <reified T : Any> T.toPayload() = buildPayload {
    data(format.encodeToString<T>(this@toPayload))
}

fun <T : Any> T?.toPayload(type: KType) = buildPayload {
    data(format.encodeToString(serializer(type), this@toPayload))
}



inline fun <reified T : Any> Payload(route: String, value: T): Payload = buildPayload {
    metadata(RoutingMetadata(route))
    data(format.encodeToString<T>(value))
}

fun Payload(route: String, data: Buffer = Buffer()): Payload = buildPayload {
    metadata(RoutingMetadata(route))
    data(data)
}
