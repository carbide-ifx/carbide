package njord.utility.event

import kotlin.reflect.KClass

fun <T : Event> topicNameFor(event: T): String = topicNameFor(event::class)
inline fun <reified T : Event> topicNameFor(): String = topicNameFor(T::class)

fun <T : Event> topicNameFor(cls: KClass<T>): String {
    val enclosing = cls.java.enclosingClass.simpleName ?: error("Event class should be nested under a manager")
    return "$enclosing.${cls.java.simpleName}"
}
