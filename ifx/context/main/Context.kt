package ifx.context

import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass


@Serializable
data class Context(val data: String = "", val number: Int = 0) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<Context> get() = Key
    companion object Key : CoroutineContext.Key<Context>
}
//
//@Serializable
//data class ProxyFactoryConfig(val port: Int = 31337, val serviceMap: Map<KClass<>, String> = mapOf()) :
//    CoroutineContext.Element {
//    override val key: CoroutineContext.Key<ProxyFactoryConfig> = Key
//
//    companion object Key : CoroutineContext.Key<ProxyFactoryConfig>
//}
