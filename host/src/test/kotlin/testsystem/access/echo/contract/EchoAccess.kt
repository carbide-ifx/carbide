package ifx.testsystem.access.echo.contract

import kotlinx.serialization.Serializable

interface EchoAccess {
    suspend fun echo(request: EchoRequest): EchoResponse
    suspend fun echoException(e: EchoRequest): EchoResponse
    suspend fun echoContext(e: EmptyEmpty): EchoResponse

    @Serializable
    object EmptyEmpty

    @Serializable
    data class EchoRequest(val number: Int)

    @Serializable
    data class EchoResponse(val number: Int)
}
