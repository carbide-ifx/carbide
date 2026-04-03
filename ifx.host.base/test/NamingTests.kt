//package ifx.host
//
//
//import ifx.naming.Naming.asComponent
//import ifx.naming.Naming.getComponent
//import ifx.naming.Naming.isContract
//import ifx.naming.Naming.isManager
//import ifx.naming.Naming.isService
//import io.kotest.assertions.assertSoftly
//import io.kotest.core.spec.style.StringSpec
//import io.kotest.matchers.shouldBe
//
//
//class NamingTests : StringSpec({
//
//    "isContract" {
//        assertSoftly{
//            deliveryManager.isContract() shouldBe true
//            flowManager.isContract() shouldBe true
//            iValidationEngine.isContract() shouldBe true
//            contentManager.isContract() shouldBe false
//            validationEngine.isContract() shouldBe false
//        }
//    }
//
//    "isService" {
//        assertSoftly{
//            deliveryManager.isService() shouldBe false
//            flowManager.isService() shouldBe false
//            iValidationEngine.isService() shouldBe false
//            contentManager.isService() shouldBe true
//            validationEngine.isService() shouldBe true
//        }
//    }
//    "isManager" {
//        assertSoftly{
//            deliveryManager.isManager() shouldBe true
//            flowManager.isManager() shouldBe true
//            iValidationEngine.isManager() shouldBe false
//            contentManager.isManager() shouldBe true
//            validationEngine.isManager() shouldBe false
//        }
//    }
//    "getComponent" {
//        assertSoftly{
//            deliveryManager.getComponent() shouldBe "DeliveryManager"
//            flowManager.getComponent() shouldBe "FlowManager"
//            iValidationEngine.getComponent() shouldBe "IValidationEngine"
//            contentManager.getComponent() shouldBe "ContentManager"
//            validationEngine.getComponent() shouldBe "ValidationEngine"
//        }
//    }
//
//    "prefix" {
//        assertSoftly("no.sonat.manager.sales.contract.customer.ISalesManager".asComponent()) {
//            assertSoftly {
//                it.prefix shouldBe "no.sonat"
//                it.concept shouldBe Manager
//                it.volatility shouldBe "sales"
//                it.aspect shouldBe Contract
//                it.facet shouldBe "customer"
//                it.component shouldBe "ISalesManager"
//            }
//        }
//
//    }
//}) {
//    companion object {
//        const val deliveryManager = "company.manager.content.contract.delivery.DeliveryManager"
//        const val flowManager = "company.manager.content.contract.flow.FlowManager"
//        const val iValidationEngine = "company.engine.validation.contract.IValidationEngine"
//
//        const val contentManager = "company.manager.content.service.ContentManager"
//        const val validationEngine = "company.engine.validation.service.ValidationEngine"
//    }
//
//}
