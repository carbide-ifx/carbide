package arve.host

import arve.ifx.MethodDescriptors
import arve.service.ServiceBase
import host.GrpcServer
import io.grpc.ServerBuilder
import java.net.ServerSocket
import kotlin.reflect.full.isSubclassOf


class Host(port: Int = 0) {
    val port: Int = if (port == 0) randomFreePort() else port

    val builder: ServerBuilder<*> = ServerBuilder.forPort(this.port)
    private lateinit var server: io.grpc.Server

    inline fun <reified TContract : Any> addService(service: TContract): Host {
        require(service::class.isSubclassOf(ServiceBase::class)) {
            "Service ${service::class} must implement Service interface"
        }

        builder.addService { MethodDescriptors.createServiceDefinition<TContract>(service) }
        return this
    }

    fun start(): Host {
        server = builder.build().start()
        return this;
    }

    fun stop(): Host {
        server.shutdown()
        return this;
    }

    companion object {
        fun randomFreePort(): Int {
            val socket =  ServerSocket(0)
            val port = socket.localPort
            socket.close()
            return port
        }
    }
}

