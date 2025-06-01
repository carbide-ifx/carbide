package ifx.proxy


import ifx.protocol.contract.InvocationException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class InstanceHandler(val instance: Any) : InvocationHandler {
    @Throws(Exception::class)
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val nonNullArgs = args ?: arrayOf()
        val continuation = nonNullArgs.continuation()

        return if (continuation == null) {
            // non-suspending function, just invoke regularly
            try {
                method.invoke(instance, *nonNullArgs)
            } catch (exception: Exception) {
                throw InvocationException(exception.cause ?: exception)

            }
        } else {
            // create a wrapper around the original continuation. we want to do this so we can capture the result and
            // potentially inspect it
            val wrappedContinuation = object : Continuation<Any?> {
                override val context: CoroutineContext get() = continuation.context

                override fun resumeWith(result: Result<Any?>) {
                    // here is where we could inspect result for any type of result / error that we'd like.
                    // since we are not doing anything special with it in this example, we can just resume the continuation
                    // with the value
                    continuation.resumeWith(result)
                }
            }

            invokeSuspendFunction(continuation) outer@{
                // we want to invoke the method with our continuation wrapper instead
                // of the original continuation so we can inspect the results. So we will
                // grab the original arguments, and replace the last element with our continuation wrapper
                val argumentsWithoutContinuation = if (nonNullArgs.isNotEmpty()) {
                    nonNullArgs.take(nonNullArgs.size - 1)
                } else {
                    nonNullArgs.toList()
                }

                val newArgs = argumentsWithoutContinuation + wrappedContinuation

                try { method.invoke(instance, *newArgs.toTypedArray()) }
                catch (invocationTargetException: Throwable) {
                    throw InvocationException(invocationTargetException.cause ?: invocationTargetException)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeSuspendFunction(continuation: Continuation<*>, block: suspend () -> T): T =
        (block as (Continuation<*>) -> T)(continuation)


    @Suppress("UNCHECKED_CAST")
    private fun Array<*>?.continuation(): Continuation<Any?>? = this?.lastOrNull() as? Continuation<Any?>
}
