package ifx.test

import ifx.service.ErrorCode
import ifx.service.Response
import io.kotest.assertions.fail
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
/**
 * Asserts that the Response is a success and returns the value.
 */
fun <T: Any> Response<T>.assertSuccess(): T = when (this) {
    is Response.Success -> value
    is Response.Failure -> fail(concatErrors(errors))
}

/**
 * Asserts that the Response is a failure and returns the errors.
 */
fun <T: Any> Response<T>.assertFailure(): List<ErrorCode> = when (this) {
    is Response.Failure -> errors
    is Response.Success -> fail("Expected failure")
}

fun <T: Any> Response<T>.assertFailureWithErrors(vararg expectedErrors: ErrorCode) {
    assertFailure() shouldContainExactlyInAnyOrder  expectedErrors.toList()
}

fun <T: Any> Response<T>.assertSuccess(assertions: T.(T) -> Unit): T = when (this) {
    is Response.Success -> assertSoftly(value, assertions)
    is Response.Failure -> fail(concatErrors(errors))
}

fun <T : Any, U : Any> Response<T>.assertSuccess(extractor: (T) -> U, assertions: U.(U) -> Unit): T = when (this) {
    is Response.Success -> {
        assertSoftly(extractor(value), assertions)
        value
    }

    is Response.Failure -> fail(concatErrors(errors))
}


fun concatErrors(errors: List<ErrorCode>) = errors.joinToString { it.message }

fun <T: Any> assertSuccess(block: () -> Response<T>): T = block().assertSuccess()
fun <T: Any> assertFailure(block: () -> Response<T>): List<ErrorCode> = block().assertFailure()
