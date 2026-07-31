import co.touchlab.kermit.Logger
import ifx.logging.Log
import kotlin.test.Test

val logger = Logger.withTag("Original Logger")
val log = Log("My Logger")

class LoggerTest {
    @Test
    fun `Log writes to the console by default`() {
        logger.i("Original Logger")
        log.info { "My Logger" }
        log.info { 42 }
    }
}
