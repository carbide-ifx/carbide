import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DummyTest {
    @Test
    fun dummy() = runTest {
        1 shouldBe 1
    }

}


//package ifx.naming
//
//
//import ifx.naming.Naming.Aspect.Contract
//import ifx.naming.Naming.Aspect.Service
//import ifx.naming.Naming.Concept.Manager
//import java.lang.reflect.Method
//import java.util.Locale
//import kotlin.coroutines.Continuation
//import kotlin.reflect.KClass
//import kotlin.reflect.KFunction
//import kotlin.reflect.jvm.javaMethod
//
//private const val s = "Invalid class name"
////   <Company>.<Concept>.<Volatility>.<Aspect>[.<Context>]
////   company.manager.content.contract.delivery.IDeliveryManager
////   company.manager.content.contract.flow.IFlowManager
////   company.manager.content.service.ContentManager
//
////   company.engine.validation.contract.IValidationEngine
////   company.engine.validation.service.ValidationEngine
//
//object Naming {
//    val namingConvention =
//        """(?<prefix>.+)\.(?<concept>access|manager|engine|utility)\.(?<volatility>\w+)\.(?<aspect>contract|service)\.?(?<facet>\w+)?\.(?<component>\w+)${'$'}""".toRegex()
//
//    fun String.isContract(): Boolean = Component(this).aspect == Contract
//    fun String.isService(): Boolean = Component(this).aspect == Service
//    fun String.isManager(): Boolean = Component(this).concept == Manager
//
//    fun String.getComponent(): String = Component(this).component
//
//
//    fun KClass<*>.isContract(): Boolean = Component(this).aspect == Contract
//    fun KClass<*>.isService(): Boolean = Component(this).aspect == Service
//    fun KClass<*>.isManager(): Boolean = Component(this).concept == Manager
//
//
//    data class Component(val cls: String) {
//        constructor(cls: KClass<*>) : this(cls.qualifiedName ?: throw IllegalArgumentException("Invalid class name: ${cls.qualifiedName}"))
//
//        val prefix: String
//        val concept: Concept
//        val volatility: String
//        val aspect: Aspect
//        val facet: String
//        val component: String
//
//        init {
//            val match = namingConvention.matchEntire(cls) ?: throw IllegalArgumentException("Invalid class name: $cls")
//            val (company, concept, volatility, aspect, facet, component) = match.destructured
//            this.prefix = company
//            this.concept = Concept.valueOf(concept.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
//            this.volatility = volatility
//            this.aspect = Aspect.valueOf(aspect.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
//            this.facet = facet
//            this.component = component
//        }
//    }
//
//    enum class Aspect { Contract, Service}
//    enum class Concept { Manager, Engine, Access, Utility }
//
//    fun String.asComponent() = Component(this);
//    fun KClass<*>.asComponent() = Component(this);
//
//
//
//
//    fun generateFullMethodName(fullServiceName: String, method: Method): String {
//        val isSingleParam = method.parameterTypes.size == 1
//        val isSingleSuspendParam = method.parameterTypes.size == 2 && method.parameterTypes[1] == Continuation::class.java
//        require(isSingleParam || isSingleSuspendParam) { "Method must have exactly one parameter or one parameter and a continuation" }
//        val paramType = method.parameterTypes.first().typeName
//        return "$fullServiceName/${method.name}#$paramType"
//    }
//    fun generateFullMethodName(fullServiceName: String, method: KFunction<*>): String =
//        generateFullMethodName(fullServiceName, method.javaMethod!!)
//}
//
//
