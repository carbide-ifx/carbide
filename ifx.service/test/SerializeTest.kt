package ifx.service

import ifx.logging.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test

@Serializable
data class Name(val first: String)

class Tests {
    @Test
    fun afdTest() {
        val r: Response<Name> = Response.Success(Name("John"))
        val serialized = Json.encodeToString(r)
        Log.info { serialized }
        val deserialized = Json.decodeFromString<Response<Name>>(serialized)
        Log.info { deserialized }
    }
}
