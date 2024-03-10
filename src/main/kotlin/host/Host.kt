package ifx.host

import ifx.ctx.BlockingContextInterceptor
import ifx.ctx.CourotineContextInterceptor
import ifx.ctx.ServerLogInterceptor
import ifx.naming.Naming.isContract
import ifx.service.ServiceBase
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import java.net.ServerSocket
import kotlin.reflect.cast


class Host(port: Int = 0) {
    val port: Int = if (port == 0) randomFreePort() else port
    val builder: ServerBuilder<*> = ServerBuilder.forPort(this.port)
    private lateinit var server: io.grpc.Server

    val serverInterceptors = listOf(
        CourotineContextInterceptor,
        BlockingContextInterceptor,
        ServerLogInterceptor()
    )

    /**
     * Add a service to the host, binding it to the given service contract
     */
    inline fun <reified TContract : Any> addService(instance: TContract): Host {
        val grpc = GrpcServer<TContract>(instance)
        builder.addService(ServerInterceptors.intercept(grpc, serverInterceptors))
        return this
    }

    /**
     * Add a service to the host, binding it to each service contract the service implements
     */
    fun addService(instance: ServiceBase): Host {
        instance.facets().forEach {
            val grpc = GrpcServer(it, it.cast(instance))
            builder.addService(ServerInterceptors.intercept(grpc, serverInterceptors))
        }
        return this
    }

    private fun ServiceBase.facets() = this::class.java.interfaces
        .map { it.kotlin }
        .filter { it.isContract() }

    fun start(): Host {
        server = builder.build().start()
        println("Starting server on port $port with services: [${server.services.joinToString { it.serviceDescriptor.name }}]")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this.stop()
                println("*** server shut down")
            },
        )
        return this;
    }

    fun stop(): Host {
        server.shutdown()
        return this;
    }


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

    companion object {
        fun randomFreePort(): Int {
            val socket = ServerSocket(0)
            val port = socket.localPort
            socket.close()
            return port
        }
    }


}

