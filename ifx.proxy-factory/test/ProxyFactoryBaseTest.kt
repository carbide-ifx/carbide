package ifx.proxy.factory

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IClientProtocol
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceEndpoint
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.TypeReference
import ifx.service.IService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProxyFactoryBaseTest {
    @Test
    fun `first proxy creation freezes copied interceptor configuration across endpoint views`() = runBlocking {
        val calls = mutableListOf<String>()
        val first = recordingInterceptor("first", calls)
        val late = recordingInterceptor("late", calls)
        val configured = mutableListOf(first)
        val factory = ProxyFactoryBase(RecordingProtocol()).addInterceptors(configured)
        configured += late

        val proxy = factory.at(ServiceEndpoint("example.test", 9000)).create(TestServiceDescriptor)
        proxy.ping()

        assertEquals(listOf("first"), calls)
        assertFailsWith<IllegalStateException> { factory.addInterceptors(late) }
        Unit
    }

    @Test
    fun `closing one view closes the shared factory once and rejects further use`() {
        val protocol = RecordingProtocol()
        val factory = ProxyFactoryBase(protocol)
        val view = factory.at(ServiceEndpoint("example.test", 9000))

        view.close()
        factory.close()

        assertEquals(1, protocol.closeCount)
        assertFailsWith<IllegalStateException> { factory.create(TestServiceDescriptor) }
        assertFailsWith<IllegalStateException> { factory.at(ServiceEndpoint("other.test", 9001)) }
        assertFailsWith<IllegalStateException> { factory.addInterceptors(recordingInterceptor("late", mutableListOf())) }
    }
}

private fun recordingInterceptor(name: String, calls: MutableList<String>) = IInterceptor { call, next ->
    calls += name
    next(call)
}

private interface ITestService : IService {
    suspend fun ping()
}

private object TestServiceDescriptor : ServiceDescriptor<ITestService> {
    override val contract = ITestService::class
    override val description = ServiceDescription(
        name = "ITestService",
        address = "test.ITestService",
        kind = ServiceKind.SERVICE,
        operations = listOf(
            OperationDescription(
                name = "ping",
                route = "ping()",
                parameterName = null,
                request = TypeReference.VoidType,
                response = TypeReference.VoidType,
                interaction = InteractionType.REQUEST_RESPONSE,
            ),
        ),
        types = emptyList(),
    )

    override fun createClient(binding: IBinding): ITestService = object : ITestService {
        override suspend fun ping() {
            binding.requestResponse("ping()", Message("{}", ""))
        }
    }

    override fun bind(instance: ITestService): IBinding = error("Not used by this test")
}

private class RecordingProtocol : IClientProtocol {
    var closeCount = 0

    override fun createClientBinding(address: String, endpoint: ServiceEndpoint?): IBinding = RecordingBinding

    override fun close() {
        closeCount++
    }
}

private object RecordingBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit
    override suspend fun requestResponse(operation: String, message: Message): Message = Message("{}", "")
    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}
