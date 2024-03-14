package ifx.proxy

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

open class ObjectInvocationHandler : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any = when (method) {
        HASH_CODE -> objectHashCode(proxy)
        EQUALS -> objectEquals(proxy, args[0])
        TO_STRING -> objectToString(proxy)
        else -> throw UnsupportedOperationException(method.name)
    }

    private fun objectClassName(obj: Any) = obj.javaClass.name

    private fun objectHashCode(obj: Any?) = System.identityHashCode(obj)

    private fun objectEquals(obj: Any, other: Any) = obj === other

    private fun objectToString(obj: Any) = objectClassName(obj) + '@' + Integer.toHexString(objectHashCode(obj))

    companion object {
        private val obj: Class<Any> = Any::class.java
        val HASH_CODE: Method = obj.getDeclaredMethod("hashCode")
        val EQUALS: Method = obj.getDeclaredMethod("equals", obj)
        val TO_STRING: Method = obj.getDeclaredMethod("toString")

    }
}
