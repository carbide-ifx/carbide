package ifx

import ifx.proxy.validatedMethods
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import kotlinx.serialization.Serializable

class HostTests : FreeSpec({
    "Configuration" - {
        "From file" {}
        "From environment" {}
        "Explicit overrides" {}
    }
    "Validation" - {
        "Naming policy" {}
        "ServiceBase" - {}
        "Service contract" - {
            shouldNotThrowAny{
                ValidContract::class.validatedMethods()
            }

        }
    }
})

interface InvalidContract {
    fun testMethod(param: Map<String, List<Letter>>)
    sealed interface Letter
    @Serializable
    data class A(val number: Int) : Letter
    data class B(val number: Int) : Letter
}
interface ValidContract {
    fun testMethod(param: Map<String, List<Letter>>)
    sealed interface Letter
    @Serializable
    data class A(val number: Int) : Letter

    @Serializable
    data class B(val number: Int) : Letter

}
