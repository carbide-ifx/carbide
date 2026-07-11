package access.product.contract

sealed interface Product {
    val id: String
    data class Car(
        override val id: String,
        val brand: String,
        val color: String
    ): Product

    data class Bike(
        override val id: String,
        val numGears: Int
    ): Product
}
