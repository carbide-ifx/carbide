package ifx.host.contract

import ifx.service.IService

interface IFireAndForget : IService {
    suspend fun fireAndForget()
    fun blockingFireAndForget()
    suspend fun fireAndForgetParam(a: String)
    fun blockingFireAndForgetParam(a: String)
    suspend fun fireAndForgetWithException()
    fun blockingFireAndForgetWithException()
}
