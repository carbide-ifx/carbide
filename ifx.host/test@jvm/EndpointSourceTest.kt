package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceKind
import io.ktor.server.application.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointSourceTest {
    @Test
    fun `listener installs only endpoints selected by its endpoint source`() = runBlocking {
        val projectedEndpoint = endpoint("product-web")
        val protocol = RecordingServerProtocol()
        val host = Host {
            listen(
                protocol = protocol,
                endpointSource = EndpointSource { listOf(projectedEndpoint) },
            )
        }

        try {
            host.start()
            assertEquals(listOf(projectedEndpoint), protocol.installedEndpoints)
        } finally {
            host.stop()
        }
    }

    @Test
    fun `listener identity permits distinct surfaces using the same protocol`() {
        val protocol = RecordingServerProtocol()
        val host = Host {
            listen(protocol, id = "customer-api")
            listen(protocol, id = "operator-api")
        }

        assertEquals(listOf("customer-api", "operator-api"), host.boundListeners.map { it.listenerId })
        assertFailsWith<IllegalArgumentException> {
            Host {
                listen(protocol, id = "duplicate")
                listen(protocol, id = "duplicate")
            }
        }
    }

    private fun endpoint(address: String) = Endpoint(
        binding = EmptyBinding,
        description = ServiceDescription(
            name = address,
            address = address,
            kind = ServiceKind.SERVICE,
            operations = emptyList(),
            types = emptyList(),
        ),
    )
}

private class RecordingServerProtocol : IServerProtocol {
    override val id: String = "recording"
    var installedEndpoints: List<Endpoint>? = null

    override fun install(application: Application, endpoints: List<Endpoint>) {
        installedEndpoints = endpoints
    }
}

private object EmptyBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit
    override suspend fun requestResponse(operation: String, message: Message): Message = message
    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}
