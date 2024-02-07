package component.access.polymorphism.contract

import kotlinx.serialization.Serializable

interface PolymorphicAccess {
    fun echo(input: RecordRequest): RecordResponse

    @Serializable
    sealed interface RecordRequest {
        @Serializable
        data class Please(val message: Int) : RecordRequest
        @Serializable
        data class Thanks(val message: String) : RecordRequest
    }

    @Serializable
    sealed interface RecordResponse {
        @Serializable
        data class Yes(val message: Int) : RecordResponse
        @Serializable
        data class No(val message: String) : RecordResponse
    }
}
