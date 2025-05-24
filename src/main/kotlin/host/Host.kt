package arve.host

import arve.service.ServiceBase
import host.GrpcServer
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import naming.Naming.isContract
import java.net.ServerSocket
import kotlin.reflect.cast


class Host(port: Int = 0) {
    val port: Int = if (port == 0) randomFreePort() else port

    val builder: ServerBuilder<*> = ServerBuilder.forPort(this.port)
    private lateinit var server: io.grpc.Server

    //    inline fun <reified TContract : Any> addService(service: TContract) = this.addService(service, TContract::class)
    inline fun <reified TContract : Any> addService(service: TContract): Host {
        builder.addService(GrpcServer<TContract>(service))
        return this
    }
    fun addService(instance: ServiceBase): Host {
        instance.facets().forEach {
            println("adding facet ${it.simpleName}")
            val grpc = GrpcServer(it, it.cast(instance))
            builder.addService(grpc)
        }
        return this
    }

    fun listServices(): List<ServerServiceDefinition> = server.services

//    fun <TContract : Any> addService(service: TContract, cls: KClass<TContract>): Host {
//        require(service::class.isSubclassOf(ServiceBase::class)) {
//            "Service ${service::class} must implement Service interface"
//        }
//        builder.addService { MethodDescriptors.createServiceDefinition(service, cls) }
//        return this
//    }
//
//    fun addService(service: ServiceBase): Host {
//        builder.addService { MethodDescriptors.createServiceDefinition(service, service::class) }
//        return this
//    }

    private fun ServiceBase.facets() = this::class.java.interfaces.map { it.kotlin }.filter { it.isContract() }

    fun start(): Host {
        server = builder.build().start()
        println("Starting server on port $port with services: [${server.services.joinToString { it.serviceDescriptor.name }}]")
        return this;
    }

    fun stop(): Host {
        server.shutdown()
        return this;
    }

    companion object {
        fun randomFreePort(): Int {
            val socket = ServerSocket(0)
            val port = socket.localPort
            socket.close()
            return port
        }
    }
}

