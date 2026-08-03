package manager.sales.service

import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import engine.pricing.contract.IPricingEngine
import ifx.proxy.contract.IProxyFactory
import ifx.proxy.contract.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import manager.sales.contract.ISalesManager
import manager.sales.contract.Product


class SalesManager(val proxyFactory: IProxyFactory) : ISalesManager {
    val productAccess get() = proxyFactory.create<IProductAccess>()
    val pricingEngine get() = proxyFactory.create<IPricingEngine>()
    override fun listProducts(): Flow<Product> = flow {
        productAccess.filter(ProductCriteria()).forEach { product ->
            val price = pricingEngine.calculatePriceNok(product.id)
            emit(product.toManager(price))
        }
    }
}

