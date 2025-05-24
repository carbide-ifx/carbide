package component.access.exception.contract

import kotlinx.serialization.Serializable

@Serializable
data class StringResult(val result: String)

@Serializable
data class IntRequest(val a: Int)
interface IExceptionAccess {
    fun test(a: IntRequest): StringResult

    fun testException(a: Int): StringResult

    suspend fun testSuspend(a: Int): StringResult

    suspend fun testExceptionSuspend(a: Int): StringResult
}


