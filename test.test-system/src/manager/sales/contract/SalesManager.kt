package manager.sales.contract

import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface ISalesManager: IService {
    fun listProducts(): Flow<Product>
}

@Serializable
data class Product(
    val id: String,
    val description: String,
    val price: Int?
)

