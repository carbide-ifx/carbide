package ifx.testsystem.manager.membership.contract

import kotlinx.serialization.Serializable

interface CustomerManager {
    suspend fun register(request: RegisterRequest): RegisterResponse

    suspend fun forwardContext(e: Empty): Int

    @Serializable
    data class RegisterResponse(val id: Int)

    @Serializable
    data class RegisterRequest(val name: String, val age: Int)

    @Serializable
    data object Empty
}
