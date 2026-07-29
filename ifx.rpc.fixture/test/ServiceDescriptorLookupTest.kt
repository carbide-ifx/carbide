package ifx.rpc.fixture

import ifx.host.HostBase
import ifx.host.IHost.Companion.registerService
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IProtocol
import ifx.protocol.contract.Message
import ifx.protocol.contract.serviceDescriptorOf
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactoryBase
import kotlinx.coroutines.flow.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServiceDescriptorLookupTest {
    @Test
    fun `service contract resolves its generated descriptor`() {
        val descriptor = serviceDescriptorOf<IFixtureService>()

        assertEquals(IFixtureService::class, descriptor.contract)
        assertEquals("ifx.rpc.fixture.IFixtureService", descriptor.address)
    }

    @Test
    fun `host registration and client creation do not require a descriptor registry`() {
        val protocol = RecordingProtocol()
        val host = HostBase(protocol)

        host.registerService<IFixtureService> { FixtureService() }
        val client = ProxyFactoryBase(protocol).create<IFixtureService>()

        assertEquals(listOf("ifx.rpc.fixture.IFixtureService"), protocol.createdClients)
        assertIs<IFixtureService>(client)
    }
}

private class RecordingProtocol : IProtocol {
    val createdClients = mutableListOf<String>()

    override fun expose(endpoint: Endpoint): IProtocol = this

    override fun createClientBinding(address: String): IBinding {
        createdClients += address
        return object : IBinding {
            override suspend fun fireAndForget(operation: String, message: Message) =
                error("Not called by this test")

            override suspend fun requestResponse(operation: String, message: Message): Message =
                error("Not called by this test")

            override suspend fun requestStream(operation: String, message: Message): Flow<Message> =
                error("Not called by this test")
        }
    }

    override fun open(): IProtocol = this

    override fun close(): IProtocol = this
}
