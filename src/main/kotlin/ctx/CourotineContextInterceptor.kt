package ifx.ctx

import ifx.ctx.Context.Key.METADATA_KEY
import io.grpc.Metadata
import io.grpc.Metadata.AsciiMarshaller
import io.grpc.ServerCall
import io.grpc.kotlin.CoroutineContextServerInterceptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import io.grpc.Context as GrpcContext


object CourotineContextInterceptor : CoroutineContextServerInterceptor() {
    override fun coroutineContext(call: ServerCall<*, *>, headers: Metadata): CoroutineContext =
        headers[METADATA_KEY] ?: EmptyCoroutineContext
}

object ContextStringMarshaller : AsciiMarshaller<Context> {
    override fun toAsciiString(value: Context): String = Json.encodeToString(value)
    override fun parseAsciiString(serialized: String): Context = Json.decodeFromString(serialized)
}

