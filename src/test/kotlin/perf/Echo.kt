package arve.test.perf

import kotlinx.serialization.Serializable

interface Echo {
    fun echo(request: EchoRequest): EchoResponse

    @Serializable
    data class EchoRequest(val message: String)

    @Serializable
    data class EchoResponse(val message: String)
}
