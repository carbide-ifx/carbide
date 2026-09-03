package ifx.protocol.jsonrpc

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.OperationDescription
import ifx.protocol.contract.ServiceDescription
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.TypeReference
import ifx.protocol.contract.InteractionType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRpcProtocolTest {
    @Test
    fun `service cancellation is preserved by the server request`() = testApplication {
        application {
            JsonRpcServerProtocol().install(this, listOf(Endpoint(CancellingBinding, serviceDescription)))
        }

        val response = client.post("/test.ICancellingService") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"cancel()","id":1}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}

private val serviceDescription = ServiceDescription(
    name = "ICancellingService",
    address = "test.ICancellingService",
    kind = ServiceKind.SERVICE,
    operations = listOf(
        OperationDescription(
            name = "cancel",
            route = "cancel()",
            parameterName = null,
            request = TypeReference.VoidType,
            response = TypeReference.VoidType,
            interaction = InteractionType.REQUEST_RESPONSE,
        ),
    ),
    types = emptyList(),
)

private object CancellingBinding : IBinding {
    override suspend fun fireAndForget(operation: String, message: Message) = Unit

    override suspend fun requestResponse(operation: String, message: Message): Message =
        throw CancellationException("cancelled")

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = emptyFlow()
}
