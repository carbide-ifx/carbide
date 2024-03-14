package ifx.context

import io.grpc.Metadata
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

@Serializable
data class Context(val data: String = "", val number: Int = 0) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key

    companion object Key : CoroutineContext.Key<Context> {
        val METADATA_KEY: Metadata.Key<Context> = Metadata.Key.of("ifx.context", ContextStringMarshaller)
    }
}

