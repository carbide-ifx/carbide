package test.service.aggregation

import access.product.contract.IProductAccess
import access.product.contract.IProductAccessDescriptor
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import ifx.subsystem.default
import ifx.subsystem.subsystem

/** Compile-time proof that descriptors are generated in the Native subsystem compilation. */
val dependencyServiceDescriptor = IProductAccessDescriptor

/** Compile-time proof that stable subsystem host conveniences are available for Native. */
fun defaultSubsystemHost(): Host = Host.default()

fun configuredSubsystemHost(): Host = Host.subsystem {
    listen(RSocketServerProtocol())
}

/** Compile-time proof that the compiler plugin links typed service registration on Native. */
suspend fun registerDependencyService(host: Host, service: IProductAccess) =
    host.registerService<IProductAccess>(service)

/** Compile-time proof that the compiler plugin links typed proxy creation on Native. */
fun dependencyServiceProxy(factory: IProxyFactory): IProductAccess = factory.create<IProductAccess>()
