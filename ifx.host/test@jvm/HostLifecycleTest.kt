package ifx.host

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import ifx.logging.installLogWriter
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceKind
import ifx.service.IService
import ifx.service.IServiceLifecycle
import ifx.service.ServiceHealth
import io.ktor.server.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HostLifecycleTest {
    @Test
    fun `a service address can only be registered once`() = runBlocking {
        val host = Host(EmptyServerProtocol)

        host.registerService(RecordingServiceDescriptor, RecordingService())

        assertFailsWith<IllegalArgumentException> {
            host.registerService(RecordingServiceDescriptor, RecordingService())
        }
        Unit
    }

    @Test
    fun `host exposes its lifecycle state`() = runBlocking {
        val host = Host(EmptyServerProtocol)
        val states = mutableListOf(host.state)

        host.start()
        states += host.state
        host.stop()
        states += host.state

        assertEquals(listOf(HostState.NEW, HostState.READY, HostState.STOPPED), states)
    }

    @Test
    fun `host health aggregates identified service health`() = runBlocking {
        val service = RecordingService(
            health = ServiceHealth(ready = false, live = true, detail = "warming cache"),
        )
        val host = Host(EmptyServerProtocol)
            .registerService(RecordingServiceDescriptor, service)
            .start()

        try {
            assertEquals(
                HostHealth(
                    state = HostState.READY,
                    ready = false,
                    live = true,
                    services = listOf(
                        ServiceHealthSnapshot(
                            serviceInterface = "test.IRecordingService",
                            ready = false,
                            live = true,
                            detail = "warming cache",
                        ),
                    ),
                ),
                host.health(),
            )
        } finally {
            host.stop()
        }
    }

    @Test
    fun `host health bounds a hanging service check`() = runBlocking {
        val service = RecordingService(healthDelay = 10.seconds)
        val host = Host(EmptyServerProtocol, healthCheckTimeout = 25.milliseconds)
            .registerService(RecordingServiceDescriptor, service)
            .start()

        try {
            val health = withTimeout(1.seconds) { host.health() }

            assertEquals(
                ServiceHealthSnapshot(
                    serviceInterface = "test.IRecordingService",
                    ready = false,
                    live = false,
                    detail = "Health check timed out after 25ms",
                ),
                health.services.single(),
            )
        } finally {
            host.stop()
        }
    }

    @Test
    fun `start initializes a managed service before returning`() = runBlocking {
        val service = RecordingService()
        val host = Host(EmptyServerProtocol)
            .registerService(RecordingServiceDescriptor, service)

        try {
            host.start()

            assertTrue(service.started)
        } finally {
            host.stop()
        }
    }

    @Test
    fun `stop shuts down managed services in reverse registration order once`() = runBlocking {
        val events = mutableListOf<String>()
        val first = RecordingService("first", events)
        val second = RecordingService("second", events)
        val host = Host(EmptyServerProtocol)
            .registerService(RecordingServiceDescriptor, first)
            .registerService(SecondRecordingServiceDescriptor, second)

        host.start()
        events.clear()

        host.stop()
        host.stop()

        assertEquals(listOf("stop:second", "stop:first"), events)
    }

    @Test
    fun `a startup failure rolls back services that already started`() = runBlocking {
        val events = mutableListOf<String>()
        val first = RecordingService("first", events)
        val second = RecordingService("second", events, failOnStart = true)
        val host = Host(EmptyServerProtocol)
            .registerService(RecordingServiceDescriptor, first)
            .registerService(SecondRecordingServiceDescriptor, second)

        assertFailsWith<IllegalStateException> { host.start() }

        assertEquals(listOf("start:first", "start:second", "stop:first"), events)
    }

    @Test
    fun `an additional log writer failure does not fail host startup`() = runBlocking {
        val writer = FailNextHostLogWriter()
        val registration = installLogWriter(writer)
        val host = Host(EmptyServerProtocol)
        try {
            host.start()

            assertEquals(HostState.READY, host.state)
        } finally {
            registration.remove()
            host.stop()
        }
    }

    @Test
    fun `a shutdown failure does not prevent remaining services from stopping`() = runBlocking {
        val events = mutableListOf<String>()
        val first = RecordingService("first", events)
        val second = RecordingService("second", events, failOnStop = true)
        val host = Host(EmptyServerProtocol)
            .registerService(RecordingServiceDescriptor, first)
            .registerService(SecondRecordingServiceDescriptor, second)
            .start()
        events.clear()

        assertFailsWith<IllegalStateException> { host.stop() }

        assertEquals(listOf("stop:second", "stop:first"), events)
    }
}

private class FailNextHostLogWriter : LogWriter() {
    private var armed = true

    override fun isLoggable(tag: String, severity: Severity): Boolean = armed && tag == "Host"

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        armed = false
        error("Host startup log failed")
    }
}

private interface IRecordingService : IService

private class RecordingService(
    private val name: String? = null,
    private val events: MutableList<String>? = null,
    private val failOnStart: Boolean = false,
    private val failOnStop: Boolean = false,
    private val health: ServiceHealth = ServiceHealth.healthy(),
    private val healthDelay: Duration = Duration.ZERO,
) : IRecordingService, IServiceLifecycle {
    var started: Boolean = false
        private set

    override suspend fun start() {
        name?.let { events?.add("start:$it") }
        if (failOnStart) error("Could not start $name")
        started = true
    }

    override suspend fun stop() {
        name?.let { events?.add("stop:$it") }
        if (failOnStop) error("Could not stop $name")
    }

    override suspend fun health(): ServiceHealth {
        delay(healthDelay)
        return health
    }
}

private object RecordingServiceDescriptor : ServiceDescriptor<IRecordingService> {
    override val contract = IRecordingService::class
    override val description = ServiceDescription(
        name = "IRecordingService",
        address = "test.IRecordingService",
        kind = ServiceKind.SERVICE,
        operations = emptyList(),
        types = emptyList(),
    )

    override fun createClient(binding: IBinding): IRecordingService = error("Not needed by this test")

    override fun bind(instance: IRecordingService): IBinding = EmptyServiceBinding
}

private object SecondRecordingServiceDescriptor : ServiceDescriptor<IRecordingService> by RecordingServiceDescriptor {
    override val description: ServiceDescription = RecordingServiceDescriptor.description.copy(
        name = "ISecondRecordingService",
        address = "test.ISecondRecordingService",
    )
    override val address: String get() = description.address
}

private object EmptyServiceBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit
    override suspend fun requestResponse(operation: String, message: Message): Message = message
    override fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}

private object EmptyServerProtocol : IServerProtocol {
    override val id: String = "empty"
    override fun install(application: Application, endpoints: List<ifx.protocol.contract.Endpoint>) = Unit
}
