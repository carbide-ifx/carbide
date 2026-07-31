import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import ifx.logging.Log
import kotlin.test.Test
import kotlin.test.assertEquals

val logger = Logger.withTag("Original Logger")
val log = Log("My Logger")

class LoggerTest {
    @Test
    fun `Log writes to the console by default`() {
        logger.i("Original Logger")
        log.info { "My Logger" }
    }

    @Test
    fun `Log instances share configurable writers`() {
        val writer = RecordingWriter()
        Log.setLogWriters(writer)
        try {
            Log("First").info { "one" }
            Log("Second").warn { "two" }

            assertEquals(
                listOf(
                    RecordedLog(Severity.Info, "First", "one"),
                    RecordedLog(Severity.Warn, "Second", "two"),
                ),
                writer.logs,
            )
        } finally {
            Log.resetConfiguration()
        }
    }
}

private data class RecordedLog(val severity: Severity, val tag: String, val message: String)

private class RecordingWriter : LogWriter() {
    val logs = mutableListOf<RecordedLog>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        logs += RecordedLog(severity, tag, message)
    }
}
