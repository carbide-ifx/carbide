package arve.test.naming

import io.kotest.core.spec.style.StringSpec
import naming.Naming.getComponent
import naming.Naming.isContract
import naming.Naming.isManager
import naming.Naming.isService

class NamingTests : StringSpec({
    val types = listOf(
        "company.manager.content.contract.delivery.DeliveryManager",
        "company.manager.content.contract.flow.FlowManager",
        "company.engine.validation.contract.IValidationEngine",
        "company.manager.content.service.ContentManager",
        "company.engine.validation.service.ValidationEngine",
        "company.engine.validation.service.ValidationEngine",
    )


    "isContract" {
        types.forEach {
            println("$it -> ${it.isContract()}")
        }
    }

    "isService" {
        types.forEach {
            println("$it -> ${it.isService()}")
        }
    }
    "isManager" {
        types.forEach {
            println("$it -> ${it.isManager()}")
        }
    }
    "getComponent" {
        types.forEach {
            println("$it -> ${it.getComponent()}")
        }
    }
})
