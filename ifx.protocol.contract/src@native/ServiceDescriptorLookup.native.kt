@file:OptIn(ExperimentalAssociatedObjects::class)

package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject

/**
 * Compiler-plugin attachment point for a generated IFX service descriptor.
 *
 * Framework users never need to reference this annotation.
 */
@AssociatedObjectKey
@Target(AnnotationTarget.CLASS)
annotation class WithIfxServiceDescriptor(
    val descriptor: KClass<out ServiceDescriptor<*>>,
)

actual fun <T : IService> serviceDescriptorOf(contract: KClass<T>): ServiceDescriptor<T> {
    val descriptor = contract.findAssociatedObject<WithIfxServiceDescriptor>()
        ?: error("No generated IFX service descriptor associated with ${contract.qualifiedName}")

    @Suppress("UNCHECKED_CAST")
    return descriptor as ServiceDescriptor<T>
}
