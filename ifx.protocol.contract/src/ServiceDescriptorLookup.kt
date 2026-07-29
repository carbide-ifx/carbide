package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass

/**
 * Finds the descriptor associated with an [IService] contract by the IFX compiler plugin.
 */
expect fun <T : IService> serviceDescriptorOf(contract: KClass<T>): ServiceDescriptor<T>

inline fun <reified T : IService> serviceDescriptorOf(): ServiceDescriptor<T> =
    serviceDescriptorOf(T::class)
