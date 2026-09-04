package test.service.aggregation

import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.proxy.factory.IProxyFactory
import ifx.proxy.factory.create
import ifx.subsystem.development

/** Compile-time proof that dependency indexes produce descriptors in the JVM subsystem. */
val dependencyServiceDescriptor = IProductAccessDescriptor

/** Compile-time proof that generated descriptors expose owner-typed operations. */
val dependencyFilterOperation = IProductAccessDescriptor.filter
val dependencyStreamOperation = IProductAccessDescriptor.generateRandowProduct
val dependencyFireAndForgetOperation = IProductAccessDescriptor.notifyProductViewed

/** Compile-time proof that the standard development host is available for JVM. */
fun developmentSubsystemHost(): Host = Host.development()

/** Compile-time proof that the compiler plugin links typed service registration on JVM. */
fun registerDependencyService(host: Host, service: IProductAccess) =
    host.registerService<IProductAccess>(service)

/** Compile-time proof that the compiler plugin links typed proxy creation on JVM. */
fun dependencyServiceProxy(factory: IProxyFactory): IProductAccess = factory.create<IProductAccess>()
