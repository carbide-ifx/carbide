package engine.pricing.service

import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import access.product.contract.Product
import engine.pricing.contract.IPricingEngine
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.singleOrNull
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
