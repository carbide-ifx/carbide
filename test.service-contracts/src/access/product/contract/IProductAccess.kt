package access.product.contract

import ifx.service.FireAndForget
import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface IProductAccess : IService {
    suspend fun filter(criteria: ProductCriteria): List<Product>
    suspend fun findById(request: FindByIdRequest): ProductId? = request.productId
    fun generateRandowProduct(): Flow<Product>
    suspend fun store(product: Product)

    @FireAndForget
    suspend fun notifyProductViewed(productId: String)
}

@Serializable
data class FindByIdRequest(
    val productId: ProductId,
)

@Serializable
data class ProductCriteria(
    val ids: Collection<String>? = null,
    val productId: ProductId? = null,
    val children: List<ProductCriteria> = emptyList(),
) {
    companion object {
        fun id(vararg id: String) = ProductCriteria(ids = id.toList())
    }
}

@JvmInline
@Serializable
value class ProductId(val value: String)
