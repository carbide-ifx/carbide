package acme.manager.membership.contract

import ifx.service.IService
import ifx.service.Response
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface IStaffManager : IService {
    suspend fun fire(request: FireStaffRequest): Response<FireStaffResponse>
}

@Serializable
data class FireStaffResponse(val success: Boolean)

@Serializable
data class FireStaffRequest(val id: String)

