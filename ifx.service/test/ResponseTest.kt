package ifx.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResponseTest {
    @Test
    fun `serializes service-specific errors by their public fields`() {
        val encoded = Json.encodeToString<Response<String>>(Response(ExampleError.NotFound))

        assertEquals(
            """{"type":"ifx.service.Response.Failure","errors":[{"code":"NotFound","message":"The requested value was not found."}]}""",
            encoded,
        )

        val decoded = assertIs<Response.Failure>(Json.decodeFromString<Response<String>>(encoded))
        assertEquals("NotFound", decoded.errors.single().code)
        assertEquals("The requested value was not found.", decoded.errors.single().message)
    }

    @Serializable
    private enum class ExampleError(override val message: String) : ErrorCode {
        NotFound("The requested value was not found.");

        override val code: String = name
    }
}
