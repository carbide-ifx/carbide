package component.manager.membership.contract

import kotlinx.serialization.Serializable

interface StaffManager {
    fun fire(request: FireStaffRequest): FireStaffResponse

    @Serializable
    data class FireStaffResponse(val success: Boolean)

    @Serializable
    data class FireStaffRequest(val id: Int)

}

interface CustomerManager {
    fun register(request: RegisterRequest): RegisterResponse

    @Serializable
    data class RegisterResponse(val id: Int)

    @Serializable
    data class RegisterRequest(val name: String, val age: Int)
}
