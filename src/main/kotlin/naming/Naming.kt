package naming


import kotlin.reflect.KClass

private const val s = "Invalid class name"
//   <Company>.<Concept>.<Volatility>.<Aspect>[.<Context>]
//   company.manager.content.contract.delivery.IDeliveryManager
//   company.manager.content.contract.flow.IFlowManager
//   company.manager.content.service.ContentManager

//   company.engine.validation.contract.IValidationEngine
//   company.engine.validation.service.ValidationEngine

object Naming {
    val namingConvention =
        """^(?<company>\w+)\.(?<concept>\w+)\.(?<volatility>\w+)\.(?<aspect>\w+)\.?(?<context>\w+)?\.(?<component>\w+)${'$'}""".toRegex()

    fun String.isContract(): Boolean = Component(this).aspect == "contract"
    fun String.isService(): Boolean = Component(this).aspect == "service"
    fun String.isManager(): Boolean = Component(this).concept == "manager"

    fun String.getComponent(): String = Component(this).component


    fun KClass<*>.isContract(): Boolean = Component(this).aspect == "contract"
    fun KClass<*>.isService(): Boolean = Component(this).aspect == "service"
    fun KClass<*>.isManager(): Boolean = Component(this).concept == "manager"


    data class Component(val cls: String) {
        constructor(cls: KClass<*>) : this(cls.qualifiedName ?: throw IllegalArgumentException(s))

        val company: String
        val concept: String
        val volatility: String
        val aspect: String
        val context: String
        val component: String

        init {
            val match = namingConvention.matchEntire(cls) ?: throw IllegalArgumentException(s)
            val (company, concept, volatility, aspect, context, component) = match.destructured
            this.company = company
            this.concept = concept
            this.volatility = volatility
            this.aspect = aspect
            this.context = context
            this.component = component
        }


    }

    fun String.asComponent() = Component(this);
    fun KClass<*>.asComponent() = Component(this);


}


