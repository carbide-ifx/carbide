package access.product.contract

import kotlinx.serialization.Serializable

@Serializable
sealed interface Product {
    val id: String

    @Serializable
    data class Car(
        override val id: String,
        val brand: String,
        val color: String
    ): Product

    @Serializable
    data class Bike(
        override val id: String,
        val numGears: Int
    ): Product
}
