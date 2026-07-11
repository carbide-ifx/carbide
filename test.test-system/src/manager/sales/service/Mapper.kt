package manager.sales.service

import manager.sales.contract.Product
import access.product.contract.Product as AccessProduct

fun AccessProduct.toManager(price: Int?): Product = Product(
    id = id,
    description = when (this) {
        is AccessProduct.Bike -> "A Bike with $numGears gears"
        is AccessProduct.Car -> "A $color car from $brand"
    },
    price = price
)
