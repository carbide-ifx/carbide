package manager.sales.service

import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import engine.pricing.contract.IPricingEngine
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product


class SalesManager(val proxyFactory: IProxyFactory) : ISalesManager {
    val productAccess get() = proxyFactory.create<IProductAccess>()
    val pricingEngine get() = proxyFactory.create<IPricingEngine>()
    override fun listProducts(): Flow<Product> = productAccess.filter(ProductCriteria())
        .map { product ->
            val price = pricingEngine.calculatePriceNok(product.id)
            product.toManager(price)
        }
}


