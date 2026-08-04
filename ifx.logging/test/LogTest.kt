package ifx.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.Test
import kotlin.test.assertEquals

class LogTest {
    @Test
    fun `structured service tags are readable on the console`() {
        val console = RecordingLogWriter()
        val logger = Logger(
            loggerConfigInit(DecodingLogWriter(console)),
            LogTagCodec.encode(
                LogTag(
                    serviceInterface = "engine.pricing.contract.IPricingEngine",
                    serviceClassName = "engine.pricing.service.PricingEngine",
                    path = listOf("Repository"),
                )
            ),
        )

        logger.i { "Price loaded" }

        assertEquals("PricingEngine.Repository", console.entries.single().tag)
        assertEquals("Price loaded", console.entries.single().message)
    }

    @Test
    fun `plain tags pass through unchanged`() {
        val console = RecordingLogWriter()
        val logger = Logger(loggerConfigInit(DecodingLogWriter(console)), "Ktor")

        logger.w { "Framework warning" }

        assertEquals("Ktor", console.entries.single().tag)
    }
}

private class RecordingLogWriter : LogWriter() {
    val entries = mutableListOf<RecordedLogEntry>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        entries += RecordedLogEntry(severity, message, tag, throwable)
    }
}

private data class RecordedLogEntry(
    val severity: Severity,
    val message: String,
    val tag: String,
    val throwable: Throwable?,
)
