package access.product.service

import access.product.contract.IProductAccess
import access.product.contract.ProductCriteria
import ifx.stdlib.filterKeysIfPresent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import access.product.contract.Product

class ProductAccessEmulator(val db: MutableMap<String, Product> = mutableMapOf()) : IProductAccess {
    override suspend fun store(product: Product) = db.set(product.id, product)
    override fun filter(criteria: ProductCriteria): Flow<Product> = db
        .filterKeysIfPresent(criteria.ids){ key, criteria -> key in criteria}
        .values
        .asFlow()


}
