package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.TypeReference
import ifx.service.IService
import ifx.service.IServiceLifecycle
import io.ktor.server.application.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostDrainTest {
    @Test
    fun `stop rejects new calls and lets accepted work finish before service shutdown`() = runBlocking {
        val events = mutableListOf<String>()
        val service = BlockingService(events)
        val protocol = CapturingProtocol()
        val host = Host(protocol)
            .registerService(BlockingServiceDescriptor, service)
            .start()
        val binding = protocol.endpoints.single().binding
        val acceptedCall = async { binding.requestResponse("work()", Message("{}", "")) }
        service.accepted.await()
        val stopping = async { host.stop() }
        while (host.state == HostState.READY) yield()

        try {
            assertFailsWith<IllegalStateException> {
                binding.requestResponse("work()", Message("{}", ""))
            }
            service.release.complete(Unit)
            acceptedCall.await()
            stopping.await()

            assertEquals(listOf("work:start", "work:end", "service:stop"), events)
        } finally {
            service.release.complete(Unit)
            acceptedCall.cancel()
            runCatching { stopping.await() }
            runCatching { host.stop() }
        }
    }
}

private interface IBlockingService : IService {
    suspend fun work()
}

private class BlockingService(
    private val events: MutableList<String>,
) : IBlockingService, IServiceLifecycle {
    val accepted = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private var calls = 0

    override suspend fun work() {
        calls += 1
        if (calls > 1) return
        events += "work:start"
        accepted.complete(Unit)
        release.await()
        events += "work:end"
    }

    override suspend fun stop() {
        events += "service:stop"
    }
}

private object BlockingServiceDescriptor : ServiceDescriptor<IBlockingService> {
    override val contract = IBlockingService::class
    override val description = ServiceDescription(
        name = "IBlockingService",
        address = "test.IBlockingService",
        kind = ServiceKind.SERVICE,
        operations = listOf(
            OperationDescription(
                name = "work",
                route = "work()",
                parameterName = null,
                request = TypeReference.VoidType,
                response = TypeReference.VoidType,
                interaction = InteractionType.REQUEST_RESPONSE,
            ),
        ),
        types = emptyList(),
    )

    override fun createClient(binding: IBinding): IBlockingService = error("Not needed by this test")

    override fun bind(instance: IBlockingService): IBinding = object : IBinding {
        override suspend fun fireAndForget(operation: String, message: Message) = Unit

        override suspend fun requestResponse(operation: String, message: Message): Message {
            check(operation == "work()")
            instance.work()
            return Message("{}", "")
        }

        override fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
    }
}

private class CapturingProtocol : IServerProtocol {
    override val id: String = "capturing"
    lateinit var endpoints: List<Endpoint>

    override fun install(application: Application, endpoints: List<Endpoint>) {
        this.endpoints = endpoints
    }
}
