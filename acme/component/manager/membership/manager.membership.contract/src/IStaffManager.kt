package acme.manager.membership.contract

import ifx.service.IService
import ifx.service.Response
import kotlinx.serialization.Serializable

interface IStaffManager : IService {
    suspend fun fire(request: FireStaffRequest): Response<FireStaffResponse>
}

@Serializable
data class FireStaffResponse(val success: Boolean)

@Serializable
data class FireStaffRequest(val id: String)

