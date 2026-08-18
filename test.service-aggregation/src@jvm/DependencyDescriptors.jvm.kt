package test.service.aggregation

import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import ifx.subsystem.default

/** Compile-time proof that dependency indexes produce descriptors in the JVM subsystem. */
val dependencyServiceDescriptor = IProductAccessDescriptor

/** Compile-time proof that generated descriptors expose owner-typed operations. */
val dependencyFilterOperation = IProductAccessDescriptor.filter
val dependencyStreamOperation = IProductAccessDescriptor.generateRandowProduct
val dependencyFireAndForgetOperation = IProductAccessDescriptor.notifyProductViewed

/** Compile-time proof that the standard default host is available for JVM. */
suspend fun defaultSubsystemHost(): Host = Host.default()

/** Compile-time proof that the compiler plugin links typed service registration on JVM. */
suspend fun registerDependencyService(host: Host, service: IProductAccess) =
    host.registerService<IProductAccess>(service)

/** Compile-time proof that the compiler plugin links typed proxy creation on JVM. */
fun dependencyServiceProxy(factory: IProxyFactory): IProductAccess = factory.create<IProductAccess>()
