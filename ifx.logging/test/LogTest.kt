package ifx.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class LogTest {
    @Test
    fun `installed writer remains until every registration is removed`() {
        val writer = RecordingLogWriter()
        val first = installLogWriter(writer)
        val second = installLogWriter(writer)
        try {
            Log("RegistrationTest").synchronous.info { "before-removal" }
            first.remove()
            first.remove()
            Log("RegistrationTest").synchronous.info { "after-first-removal" }
            second.close()
            Log("RegistrationTest").synchronous.info { "after-final-removal" }
        } finally {
            first.remove()
            second.remove()
        }

        assertEquals(
            listOf("before-removal", "after-first-removal"),
            writer.entries.map(RecordedLogEntry::message),
        )
    }

    @Test
    fun `one failing installed writer does not block logging or other writers`() {
        val failing = installLogWriter(FailingLogWriter())
        val recordingWriter = RecordingLogWriter()
        val recording = installLogWriter(recordingWriter)
        try {
            Log("WriterIsolationTest").synchronous.info { "still-delivered" }
        } finally {
            failing.remove()
            recording.remove()
        }

        assertEquals(listOf("still-delivered"), recordingWriter.entries.map(RecordedLogEntry::message))
    }

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

    @Test
    fun `structured tag can retain metadata without rendering console fluff`() {
        val console = RecordingLogWriter()
        val logger = Logger(
            loggerConfigInit(DecodingLogWriter(console)),
            LogTagCodec.encode(
                LogTag(
                    serviceInterface = "access.product.contract.IProductAccess",
                    serviceClassName = "access.product.service.ProductAccessEmulator",
                    path = listOf("OpenTelemetryRpcInterceptor"),
                    display = false,
                    traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
                ),
            ),
        )

        logger.i { "ISalesManager -> Proxy -> IProductAccess.filter(...)" }

        assertEquals("", console.entries.single().tag)
        assertEquals(
            "ISalesManager -> Proxy -> IProductAccess.filter(...)",
            console.entries.single().message,
        )
    }

    @Test
    fun `correlation is available from coroutine context`() = runBlocking {
        val correlation = LogCorrelation(
            traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
            spanId = "00f067aa0ba902b7",
            traceFlags = "01",
        )
        val writer = CorrelationRecordingLogWriter()
        val registration = installLogWriter(writer)

        try {
            assertEquals(null, LogCorrelation.currentOrNull())
            withContext(correlation) {
                assertEquals(correlation, LogCorrelation.currentOrNull())
                Log("Application").info { "automatically-correlated-log" }
            }
        } finally {
            registration.remove()
        }

        val tag = requireNotNull(LogTagCodec.decodeOrNull(writer.entries.single().tag))
        assertEquals(correlation.traceId, tag.traceId)
        assertEquals(correlation.spanId, tag.spanId)
        assertEquals(correlation.traceFlags, tag.traceFlags)
        assertEquals("Application", tag.displayTag())
    }

    @Test
    fun `ambient service scope supplies the emitted service identity`() = runBlocking {
        val scope = ServiceLogScope(
            serviceInterface = "engine.pricing.contract.IPricingEngine",
            serviceClassName = "engine.pricing.service.PricingEngine",
        )
        val writer = ServiceRecordingLogWriter()
        val registration = installLogWriter(writer)

        try {
            withContext(scope) {
                Log(LogTag(serviceClassName = "wrong.StandaloneIdentity"))
                    .withTag("Repository")
                    .info { "service-scoped-log" }
            }
        } finally {
            registration.remove()
        }

        val tag = requireNotNull(LogTagCodec.decodeOrNull(writer.entries.single().tag))
        assertEquals(scope.serviceInterface, tag.serviceInterface)
        assertEquals(scope.serviceClassName, tag.serviceClassName)
        assertEquals(listOf("Repository"), tag.path)
    }

    @Test
    fun `log tag codec carries OpenTelemetry correlation fields`() {
        val tag = LogTag(
            serviceInterface = "engine.pricing.contract.IPricingEngine",
            traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
            spanId = "00f067aa0ba902b7",
            traceFlags = "01",
        )

        assertEquals(tag, LogTagCodec.decodeOrNull(LogTagCodec.encode(tag)))
    }
}

private class FailingLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        error("writer unavailable")
    }
}

private class CorrelationRecordingLogWriter : LogWriter() {
    val entries = mutableListOf<RecordedLogEntry>()

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        LogTagCodec.decodeOrNull(tag)?.traceId != null

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        entries += RecordedLogEntry(severity, message, tag, throwable)
    }
}

private class ServiceRecordingLogWriter : LogWriter() {
    val entries = mutableListOf<RecordedLogEntry>()

    override fun isLoggable(tag: String, severity: Severity): Boolean =
        LogTagCodec.decodeOrNull(tag)?.serviceInterface == "engine.pricing.contract.IPricingEngine"

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        entries += RecordedLogEntry(severity, message, tag, throwable)
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
