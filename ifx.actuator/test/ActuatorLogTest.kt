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

class ActuatorLogTest {
    @Test
    fun `structured service tags are retained by actuators`() {
        val console = RecordingLogWriter()
        val store = ActuatorLogStore()
        val logger = logger(
            LogTag(
                serviceInterface = PRICING_INTERFACE,
                serviceClassName = "engine.pricing.service.PricingEngine",
                path = listOf("Repository"),
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
        assertEquals(ActuatorLogSeverity.Info, entry.severity)
        assertEquals("Price loaded", entry.message)
        assertNull(entry.throwable)
    }

    @Test
    fun `plain tags are not retained by actuators`() {
        val store = ActuatorLogStore()
        val logger = Logger(loggerConfigInit(ActuatorLogWriter(store)), "Ktor")

        logger.w { "Framework warning" }

        assertTrue(store.serviceInterfaces().isEmpty())
    }

    @Test
    fun `each service retains only its configured capacity in sequence order`() {
        val store = ActuatorLogStore(capacityPerService = 3)
        val writer = ActuatorLogWriter(store)
        val encodedTag = LogTagCodec.encode(LogTag(serviceInterface = PRICING_INTERFACE))

        repeat(5) { index -> writer.log(Severity.Info, "message-$index", encodedTag) }

        val entries = store.logs(PRICING_INTERFACE)
        assertEquals(listOf(3L, 4L, 5L), entries.map(ActuatorLogEntry::sequence))
        assertEquals(listOf("message-2", "message-3", "message-4"), entries.map(ActuatorLogEntry::message))
    }

    @Test
    fun `latest flow replays the retained tail and continues with future entries`() = runBlocking {
        val store = ActuatorLogStore(capacityPerService = 3)
        val writer = ActuatorLogWriter(store)
        val encodedTag = LogTagCodec.encode(LogTag(serviceInterface = PRICING_INTERFACE))

        repeat(3) { index -> writer.log(Severity.Info, "message-$index", encodedTag) }
        val entries = mutableListOf<ActuatorLogEntry>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            store.latest(PRICING_INTERFACE).take(4).toList(entries)
        }

        writer.log(Severity.Info, "message-3", encodedTag)
        collection.join()

        assertEquals(listOf(1L, 2L, 3L, 4L), entries.map(ActuatorLogEntry::sequence))
    }

    @Test
    fun `concurrent writes retain the latest entries without an older write replacing them`() = runBlocking {
        val capacity = 500
        val writesPerWorker = 200
        val workers = 8
        val store = ActuatorLogStore(capacity)
        val writer = ActuatorLogWriter(store)
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
            store.logs(PRICING_INTERFACE).map(ActuatorLogEntry::sequence),
        )
    }

    private fun logger(
        tag: LogTag,
        console: RecordingLogWriter,
        store: ActuatorLogStore,
    ): Logger = Logger(
        loggerConfigInit(
            DecodingLogWriter(console),
            ActuatorLogWriter(store),
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
