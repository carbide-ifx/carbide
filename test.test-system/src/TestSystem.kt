import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import engine.pricing.contract.IPricingEngine
import engine.pricing.service.PricingEngine
import ifx.host.IHost.Companion.registerService
import ifx.host.rsocket.Host
import ifx.protocol.contract.ServiceRegistry
import ifx.protocol.contract.interceptors.Encryption
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.proxy.factory.ProxyFactory
import manager.sales.contract.ISalesManager
import manager.sales.service.SalesManager

fun main() {
    val interceptors = listOf(LoggingInterceptor("test"), Encryption)
    val serviceRegistry = ServiceRegistry(emptyList())


    val host = Host(0, serviceRegistry, "Test System")
        .addInterceptors(interceptors)
    val pf = ProxyFactory.forHost(host)

    host.registerService<IProductAccess> { ProductAccessEmulator() }
        .registerService<IPricingEngine> { PricingEngine(pf) }
        .registerService<ISalesManager> { SalesManager(pf) }
        .open()

}



