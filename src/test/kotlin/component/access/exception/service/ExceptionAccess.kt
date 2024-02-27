package component.access.exception.service

import arve.service.ServiceBase
import component.access.exception.contract.EmptyRequest
import component.access.exception.contract.IExceptionAccess
import component.access.exception.contract.IntRequest
import component.access.exception.contract.StringResult
import ctx.Context
import io.grpc.kotlin.GrpcContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.reflect.full.callSuspend
import kotlin.time.Duration.Companion.seconds

class ExceptionAccess : IExceptionAccess, ServiceBase() {
    override fun test(a: IntRequest): StringResult = StringResult("test result")

    override fun testException(a: Int): StringResult = throw RuntimeException("You called testException().")
    override suspend fun testSuspend(a: Int): StringResult {
        delay(0.1.seconds)
        return StringResult("testSuspend result")
    }

     override suspend fun suspendContext(e: EmptyRequest): StringResult {
        delay(0.1.seconds)
        val context = currentCoroutineContext()[Context]
        return StringResult(context?.data ?: "")
    }

    override suspend fun testExceptionSuspend(a: Int): StringResult {
        delay(0.1.seconds)
        throw Exception("You called testExceptionSuspend().")
    }
}

