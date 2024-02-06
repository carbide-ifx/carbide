package arve.ifx

import io.grpc.MethodDescriptor.Marshaller
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.lang.reflect.Type

@OptIn(ExperimentalSerializationApi::class)
object MarshallerFactory {
    inline fun <reified T : Any> json() = object : Marshaller<T> {
        override fun stream(value: T): InputStream = Json.encodeToString<T>(value).byteInputStream()
        override fun parse(stream: InputStream): T = Json.decodeFromStream<T>(stream)
    }
}


@OptIn(ExperimentalSerializationApi::class)
object MarshallerFactoryDynamic {
    fun <T : Any> json(type: Type) = object : Marshaller<T> {
        override fun stream(value: T): InputStream = Json.encodeToString(serializer(type), value).byteInputStream()
        @Suppress("UNCHECKED_CAST")
        override fun parse(stream: InputStream): T = Json.decodeFromStream(serializer(type), stream) as T
    }
}
