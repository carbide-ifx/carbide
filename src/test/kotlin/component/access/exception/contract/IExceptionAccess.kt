package component.access.exception.contract

import kotlinx.serialization.Serializable

@Serializable
data class StringResult(val result: String)

@Serializable
data class IntRequest(val a: Int)

@Serializable
object EmptyRequest

interface IExceptionAccess {
    fun test(a: IntRequest): StringResult

    fun testException(a: Int): StringResult

    suspend fun testSuspend(a: Int): StringResult

    suspend fun testExceptionSuspend(a: Int): StringResult
    suspend fun suspendContext(e: EmptyRequest = EmptyRequest): StringResult
}


