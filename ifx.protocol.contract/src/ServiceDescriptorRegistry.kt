package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass

/** Reflection-free descriptor lookup assembled at an application boundary. */
interface ServiceDescriptorRegistry {
    fun <T : IService> find(contract: KClass<T>): ServiceDescriptor<T>?
}

/** Compatibility lookup for modules that still generate descriptors beside contracts. */
object PlatformServiceDescriptorRegistry : ServiceDescriptorRegistry {
    override fun <T : IService> find(contract: KClass<T>): ServiceDescriptor<T>? =
        runCatching { serviceDescriptorOf(contract) }.getOrNull()
}

class CompositeServiceDescriptorRegistry(
    private vararg val registries: ServiceDescriptorRegistry,
) : ServiceDescriptorRegistry {
    override fun <T : IService> find(contract: KClass<T>): ServiceDescriptor<T>? =
        registries.firstNotNullOfOrNull { it.find(contract) }
}

fun <T : IService> ServiceDescriptorRegistry.requireDescriptor(contract: KClass<T>): ServiceDescriptor<T> =
    find(contract) ?: error(
        "No generated IFX service descriptor found for ${serviceAddressOf(contract)}. " +
            "Generate the subsystem service registry and pass it to the host or proxy factory.",
    )

fun <T : IService> serviceAddressOf(contract: KClass<T>): String =
    contract.qualifiedName ?: error("IFX service contracts must have a qualified name")

inline fun <reified T : IService> serviceAddressOf(): String = serviceAddressOf(T::class)
