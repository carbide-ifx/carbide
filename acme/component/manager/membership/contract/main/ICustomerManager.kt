package acme.manager.membership.contract

import ifx.service.IService
import ifx.service.Response
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface ICustomerManager : IService {
    suspend fun register(request: RegisterRequest): Response<RegisterResponse>

    suspend fun forwardContext(e: Empty): Int
}

@Serializable
data class RegisterResponse(val id: String)

@Serializable
data class RegisterRequest(val name: String, val age: Int)

@Serializable
data object Empty
