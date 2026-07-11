package manager.sales.contract

import ifx.service.IService
import kotlinx.coroutines.flow.Flow

interface ISalesManager: IService {
    fun listProducts(): Flow<Product>
}


data class Product(
    val id: String,
    val description: String,
    val price: Int?
)


