package engine.pricing.contract

import ifx.service.IService

interface IPricingEngine : IService {
    suspend fun calculatePriceNok(productId: String): Int?
}
