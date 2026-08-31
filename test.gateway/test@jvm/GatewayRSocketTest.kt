package test.gateway

import ifx.context.Context
import ifx.host.EndpointSource
import ifx.host.Host
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.InteractionType
import ifx.protocol.contract.Message
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.TypeReference
import ifx.protocol.contract.context
import ifx.protocol.rsocket.RSocketClientProtocol
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.protocol.rsocket.RSocketSetupAuthenticator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GatewayRSocketTest {
    @Test
    fun `setup authentication establishes context and discards client supplied context`() = runBlocking {
        val target = ContextRecordingBinding()
        var setupData: String? = null
        val endpoint = gatewayEndpoint(target)
        val host = Host {
            listen(
                protocol = RSocketServerProtocol(
                    authenticator = RSocketSetupAuthenticator { payload ->
                        setupData = payload.data.readString()
                        Context.Empty
                    },
                ),
                endpointSource = EndpointSource { listOf(endpoint) },
            )
        }.start()
        val protocol = RSocketClientProtocol(
            baseUrl = { "ws://localhost:${host.port("rsocket")}" },
            connectTimeout = 2.seconds,
            setupData = { "{\"bearer\":\"token\"}" },
        )

        try {
            protocol.createClientBinding("product-web").requestResponse(
                "productAccess/filter",
                Message("{\"ifx.context\":{\"spoofed\":true}}", "{}"),
            )

            assertEquals("{\"bearer\":\"token\"}", setupData)
            assertTrue(target.message?.context()?.isEmpty == true)
        } finally {
            protocol.close()
            host.stop()
        }
    }
}

private class ContextRecordingBinding : IBinding {
    var message: Message? = null

    override suspend fun fireAndForget(operation: String, message: Message) {
        this.message = message
    }

    override suspend fun requestResponse(operation: String, message: Message): Message {
        this.message = message
        return Message("{}", "{}")
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        this.message = message
        return emptyFlow()
    }
}

private fun gatewayEndpoint(binding: IBinding): Endpoint = Endpoint(
    binding = binding,
    description = ServiceDescription(
        name = "product-web",
        address = "product-web",
        kind = ServiceKind.SERVICE,
        operations = listOf(
            OperationDescription(
                name = "filter",
                route = "productAccess/filter",
                parameterName = "criteria",
                request = TypeReference.Named("Criteria"),
                response = TypeReference.Named("Product"),
                interaction = InteractionType.REQUEST_RESPONSE,
            ),
        ),
        types = emptyList(),
    ),
)
