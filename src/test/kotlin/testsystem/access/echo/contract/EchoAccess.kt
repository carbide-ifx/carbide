package ifx.testsystem.access.echo.contract

import kotlinx.serialization.Serializable

interface EchoAccess {
    fun echo(request: EchoRequest): EchoResponse
    fun echoException(e: EchoRequest): EchoResponse
    fun echoContext(e: EmptyEmpty): EchoResponse

    suspend fun echoSuspend(request: EchoRequest): EchoResponse
    fun echoExceptionSuspend(e: EchoRequest): EchoResponse
    suspend fun echoContextSuspend(e: EmptyEmpty): EchoResponse


    @Serializable
    object EmptyEmpty

    @Serializable
    data class EchoRequest(val number: Int)

    @Serializable
    data class EchoResponse(val number: Int)
}
