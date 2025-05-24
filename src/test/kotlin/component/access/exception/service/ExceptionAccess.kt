package component.access.exception.service

import component.access.exception.contract.IExceptionAccess
import component.access.exception.contract.IntRequest
import component.access.exception.contract.StringResult
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class ExceptionAccess : IExceptionAccess {
    override fun test(a: IntRequest): StringResult = StringResult("test result")

    override fun testException(a: Int): StringResult = throw RuntimeException("You called testException().")
    override suspend fun testSuspend(a: Int): StringResult {
        delay(1.seconds)
        return StringResult("testSuspend result")
    }
    override suspend fun testExceptionSuspend(a: Int): StringResult {
        delay(1000)
        throw Exception("You called testExceptionSuspend().")
    }
}
