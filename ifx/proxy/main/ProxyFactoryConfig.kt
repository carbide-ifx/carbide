package ifx.proxy

import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

@Serializable
data class ProxyFactoryConfig(
    val port: Int = 31337,
    val serviceMap: Map<String, String> = mapOf()
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<ProxyFactoryConfig> = Key
    companion object Key : CoroutineContext.Key<ProxyFactoryConfig>
}
