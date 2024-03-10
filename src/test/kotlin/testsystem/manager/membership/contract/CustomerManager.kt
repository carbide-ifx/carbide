package ifx.testsystem.manager.membership.contract

import kotlinx.serialization.Serializable

interface CustomerManager {
    fun register(request: RegisterRequest): RegisterResponse

    fun forwardContext(e: Empty): Int

    suspend fun forwardContextSuspend(e: Empty): Int

    @Serializable
    data class RegisterResponse(val id: Int)

    @Serializable
    data class RegisterRequest(val name: String, val age: Int)

    @Serializable
    data object Empty
}
