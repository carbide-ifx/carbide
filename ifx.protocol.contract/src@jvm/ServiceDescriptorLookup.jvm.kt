package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass

actual fun <T : IService> serviceDescriptorOf(contract: KClass<T>): ServiceDescriptor<T> {
    val contractName = contract.qualifiedName
        ?: error("IFX service contracts must have a qualified name")
    val descriptorClassName = "${contractName}Descriptor"

    val descriptor = try {
        contract.java.classLoader
            .loadClass(descriptorClassName)
            .getField("INSTANCE")
            .get(null)
    } catch (cause: ReflectiveOperationException) {
        error("No generated IFX service descriptor found for $contractName: ${cause.message}")
    }

    @Suppress("UNCHECKED_CAST")
    return descriptor as? ServiceDescriptor<T>
        ?: error("$descriptorClassName is not an IFX service descriptor")
}
