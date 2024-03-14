package ifx.testsystem.access.echo.service

import ifx.service.ServiceBase
import ifx.testsystem.access.echo.contract.EchoAccess
import ifx.testsystem.access.echo.contract.EchoAccess.EchoRequest
import ifx.testsystem.access.echo.contract.EchoAccess.EchoResponse
import ifx.testsystem.access.echo.contract.EchoAccess.EmptyEmpty
import io.github.oshai.kotlinlogging.KotlinLogging

class EchoAccessService : EchoAccess, ServiceBase() {
    private val log = KotlinLogging.logger { }
    override suspend fun echo(request: EchoRequest) = EchoResponse(request.number)
    override suspend fun echoException(e: EchoRequest): EchoResponse {
        log.error { "Server exception, on purpose :)" }
        throw IllegalArgumentException(e.number.toString())
//        TODO("Not yet implemented")
    }
    override suspend fun echoContext(e: EmptyEmpty) = EchoResponse(getContext().number)
}
