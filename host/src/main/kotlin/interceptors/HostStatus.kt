package ifx.host.interceptors

import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl

class HostStatus : AbstractCoroutineServerImpl() {

    override fun bindService(): ServerServiceDefinition {
        val serviceName = ""
        val ssd = ServerServiceDefinition.builder(serviceName)

        return ssd.build()
    }
}

