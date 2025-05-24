package arve.test.perf

import arve.service.ServiceBase

class EchoService : Echo, ServiceBase() {
    override fun echo(request: Echo.EchoRequest) = Echo.EchoResponse(request.message)
}
