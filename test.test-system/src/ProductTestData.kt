import access.product.contract.IProductAccess
import access.product.contract.Product

object ProductTestData {
    val car = Product.Car("car-1", "Volvo", "blue")
    val bike = Product.Bike("bike-1", 12)
    val all = listOf(car, bike)
}

suspend fun IProductAccess.seedTestData() = ProductTestData.all.forEach {
    store(it)
}
