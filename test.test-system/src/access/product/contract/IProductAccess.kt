package access.product.contract

import ifx.service.IService
import kotlinx.coroutines.flow.Flow


interface IProductAccess: IService {
    fun filter(criteria: ProductCriteria): Flow<Product>
    suspend fun store(product: Product)
}

data class ProductCriteria(
    val ids: Collection<String>? = null,
) {
    companion object {
        fun id(vararg id: String) = ProductCriteria(ids = id.toList())
    }
}
