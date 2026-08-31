package engine.pricing.service

import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import access.product.contract.Product
import engine.pricing.contract.IPricingEngine
import ifx.proxy.factory.IProxyFactory
import ifx.proxy.factory.create
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class PricingEngine(val proxyFactory: IProxyFactory) : IPricingEngine {
    val productAccess get() = proxyFactory.create<IProductAccess>()


    // Todo: Calculate price based on User (Context) "membership level"
    override suspend fun calculatePriceNok(productId: String): Int? {
        val product = productAccess.filter(ProductCriteria.id(productId)).singleOrNull()
        delay(300.milliseconds)
        return when (product) {
            is Product.Bike -> 3500
            is Product.Car -> 250000
            null -> null
        }
    }
}
