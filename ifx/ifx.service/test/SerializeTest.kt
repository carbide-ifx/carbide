package ifx.service

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
        println(serialized)
        val deserialized = Json.decodeFromString<Response<Name>>(serialized)
        println(deserialized)
    }
}
