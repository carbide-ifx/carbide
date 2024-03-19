package ifx

import ifx.naming.Naming.Aspect.Contract
import ifx.naming.Naming.Concept.Manager
import ifx.naming.Naming.asComponent
import ifx.naming.Naming.getComponent
import ifx.naming.Naming.isContract
import ifx.naming.Naming.isManager
import ifx.naming.Naming.isService
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe


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

    "prefix" {
        assertSoftly("no.sonat.manager.sales.contract.customer.ISalesManager".asComponent()) {
            assertSoftly {
                it.prefix shouldBe "no.sonat"
                it.concept shouldBe Manager
                it.volatility shouldBe "sales"
                it.aspect shouldBe Contract
                it.facet shouldBe "customer"
                it.component shouldBe "ISalesManager"
            }
        }

    }
})
