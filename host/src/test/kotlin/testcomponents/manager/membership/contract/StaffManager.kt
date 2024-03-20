package ifx.testsystem.manager.membership.contract

import kotlinx.serialization.Serializable

interface StaffManager {
    suspend fun fire(request: FireStaffRequest): FireStaffResponse

    @Serializable
    data class FireStaffResponse(val success: Boolean)

    @Serializable
    data class FireStaffRequest(val id: Int)

}

