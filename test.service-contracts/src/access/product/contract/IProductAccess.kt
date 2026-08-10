package access.product.contract

import ifx.service.FireAndForget
import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface IProductAccess : IService {
    suspend fun filter(criteria: ProductCriteria): List<Product>
    fun generateRandowProduct(): Flow<Product>
    suspend fun store(product: Product)

    @FireAndForget
    suspend fun notifyProductViewed(productId: String)
}

@Serializable
data class ProductCriteria(
    val ids: Collection<String>? = null,
) {
    companion object {
        fun id(vararg id: String) = ProductCriteria(ids = id.toList())
    }
}
