import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.print
import com.lemonappdev.konsist.api.ext.list.withParentOf
import ifx.service.IService
import io.kotest.core.spec.style.FreeSpec

class ComponentNamingPolicy : FreeSpec({
    val all = Konsist.scopeFromProduction().print()
    "Service interfaces" - {
        val contracts = all.interfaces().withParentOf(IService::class).print()
        "Package location" - {}
        "Naming" {}

        "Methods" - {
            "Return single value" - {}
            "Accept single parameter" - {}
            "Parameter is @Serializable" - {}
            "Return type is @Serializable" - {}
        }
    }

})
