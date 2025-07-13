package ifx.protocol.contract

import ifx.service.IService
import kotlin.reflect.KClass

data class Endpoint<T: IService>(
    val address: String,
    val binding: IBinding,
    val contract: KClass<T>
)
