package ifx.actuator

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import ifx.logging.DecodingLogWriter
import ifx.logging.LogTag
import ifx.logging.LogTagCodec
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogTailTest {
    @Test
    fun `structured service tags are retained by actuators`() {
        val console = RecordingLogWriter()
        val store = LogTailStore()
        val logger = logger(
            LogTag(
                serviceInterface = PRICING_INTERFACE,
                serviceClassName = "engine.pricing.service.PricingEngine",
                path = listOf("Repository"),
                traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
                spanId = "00f067aa0ba902b7",
                traceFlags = "01",
            ),
            console,
            store,
        )

        logger.i { "Price loaded" }

        assertEquals("PricingEngine.Repository", console.entries.single().tag)
        val entry = store.logs(PRICING_INTERFACE).single()
        assertEquals(1L, entry.sequence)
        assertEquals(PRICING_INTERFACE, entry.serviceInterface)
        assertEquals("engine.pricing.service.PricingEngine", entry.serviceClassName)
        assertEquals(listOf("Repository"), entry.path)
        assertEquals(LogTailSeverity.Info, entry.severity)
        assertEquals("Price loaded", entry.message)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", entry.traceId)
        assertEquals("00f067aa0ba902b7", entry.spanId)
        assertEquals("01", entry.traceFlags)
        assertNull(entry.throwable)
    }

    @Test
    fun `plain tags are not retained by actuators`() {
        val store = LogTailStore()
        val logger = Logger(loggerConfigInit(LogTailWriter(store)), "Ktor")

        logger.w { "Framework warning" }

        assertTrue(store.serviceInterfaces().isEmpty())
    }

    @Test
    fun `structured diagnostics marked as non-retained are not retained by actuators`() {
        val store = LogTailStore()
        val writer = LogTailWriter(store)
        val encodedTag = LogTagCodec.encode(
            LogTag(
                serviceInterface = PRICING_INTERFACE,
                retained = false,
            ),
        )

        assertEquals(false, writer.isLoggable(encodedTag, Severity.Info))
        writer.log(Severity.Info, "RPC response", encodedTag, null)

        assertTrue(store.serviceInterfaces().isEmpty())
    }

    @Test
    fun `each service retains only its configured capacity in sequence order`() {
        val store = LogTailStore(capacityPerService = 3)
        val writer = LogTailWriter(store)
        val encodedTag = LogTagCodec.encode(LogTag(serviceInterface = PRICING_INTERFACE))

        repeat(5) { index -> writer.log(Severity.Info, "message-$index", encodedTag) }

        val entries = store.logs(PRICING_INTERFACE)
        assertEquals(listOf(3L, 4L, 5L), entries.map(LogTailEntry::sequence))
        assertEquals(listOf("message-2", "message-3", "message-4"), entries.map(LogTailEntry::message))
    }

    @Test
    fun `latest flow replays the retained tail and continues with future entries`() = runBlocking {
        val store = LogTailStore(capacityPerService = 3)
        val writer = LogTailWriter(store)
        val encodedTag = LogTagCodec.encode(LogTag(serviceInterface = PRICING_INTERFACE))

        repeat(3) { index -> writer.log(Severity.Info, "message-$index", encodedTag) }
        val entries = mutableListOf<LogTailEntry>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            store.latest(PRICING_INTERFACE).take(4).toList(entries)
        }

        writer.log(Severity.Info, "message-3", encodedTag)
        collection.join()

        assertEquals(listOf(1L, 2L, 3L, 4L), entries.map(LogTailEntry::sequence))
    }

    @Test
    fun `concurrent writes retain the latest entries without an older write replacing them`() = runBlocking {
        val capacity = 500
        val writesPerWorker = 200
        val workers = 8
        val store = LogTailStore(capacity)
        val writer = LogTailWriter(store)
        val encodedTag = LogTagCodec.encode(LogTag(serviceInterface = PRICING_INTERFACE))

        coroutineScope {
            repeat(workers) { worker ->
                launch(Dispatchers.Default) {
                    repeat(writesPerWorker) { index ->
                        writer.log(Severity.Debug, "$worker-$index", encodedTag)
                    }
                }
            }
        }

        val totalWrites = workers * writesPerWorker
        assertEquals(
            ((totalWrites - capacity + 1).toLong()..totalWrites.toLong()).toList(),
            store.logs(PRICING_INTERFACE).map(LogTailEntry::sequence),
        )
    }

    private fun logger(
        tag: LogTag,
        console: RecordingLogWriter,
        store: LogTailStore,
    ): Logger = Logger(
        loggerConfigInit(
            DecodingLogWriter(console),
            LogTailWriter(store),
        ),
        LogTagCodec.encode(tag),
    )

    private companion object {
        const val PRICING_INTERFACE = "engine.pricing.contract.IPricingEngine"
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
