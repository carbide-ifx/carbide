package ifx.ctx

import io.grpc.Metadata
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext
import io.grpc.Context as GrpcContext

@Serializable
data class Context(val data: String="", val number: Int = 0 ) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key
    companion object Key : CoroutineContext.Key<Context> {
        val BLOCKING_CONTEXT_KEY = GrpcContext.key<Context>("ifx.context")
        val METADATA_KEY: Metadata.Key<Context> = Metadata.Key.of("ifx.context", ContextStringMarshaller)

    }
}

