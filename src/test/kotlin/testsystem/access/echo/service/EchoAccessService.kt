package ifx.testsystem.access.echo.service

import ifx.service.ServiceBase
import ifx.testsystem.access.echo.contract.EchoAccess
import ifx.testsystem.access.echo.contract.EchoAccess.EchoRequest
import ifx.testsystem.access.echo.contract.EchoAccess.EchoResponse
import ifx.testsystem.access.echo.contract.EchoAccess.EmptyEmpty

class EchoAccessService : EchoAccess, ServiceBase() {
    override fun echo(request: EchoRequest) = EchoResponse(request.number)
    override fun echoException(e: EchoRequest): EchoResponse = throw RuntimeException("You called testException().")
    override fun echoContext(e: EmptyEmpty) = EchoResponse(getBlockingContext().number)


    override suspend fun echoSuspend(request: EchoRequest) = EchoResponse(request.number)
    override fun echoExceptionSuspend(e: EchoRequest): EchoResponse = TODO("Not yet implemented")
    override suspend fun echoContextSuspend(e: EmptyEmpty) = EchoResponse(getContext().number)

}
