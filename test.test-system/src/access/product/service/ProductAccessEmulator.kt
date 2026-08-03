package access.product.service

import access.product.contract.IProductAccess
import access.product.contract.Product
import access.product.contract.ProductCriteria
import ifx.stdlib.filterKeysIfPresent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class ProductAccessEmulator(val db: MutableMap<String, Product> = mutableMapOf()) : IProductAccess {
    override suspend fun store(product: Product) = db.set(product.id, product)
    override suspend fun notifyProductViewed(productId: String) = Unit

    override suspend fun filter(criteria: ProductCriteria): List<Product> = db
        .filterKeysIfPresent(criteria.ids){ key, criteria -> key in criteria}
        .values
        .toList()

    override fun generateRandowProduct(): Flow<Product> = flow {
        repeat(50) { index ->
            delay(300.milliseconds)
            emit(randomProduct(index))
        }
    }

    private fun randomProduct(index: Int): Product = if (Random.nextBoolean()) {
        Product.Car(
            id = "random-car-$index",
            brand = listOf("Audi", "Tesla", "Toyota", "Volvo").random(),
            color = listOf("black", "blue", "red", "white").random(),
        )
    } else {
        Product.Bike(
            id = "random-bike-$index",
            numGears = Random.nextInt(from = 1, until = 31),
        )
    }
}
