package ifx.host

import ifx.context.CourotineContextInterceptor
import ifx.host.interceptors.ExceptionHandler
import ifx.host.interceptors.ServerLogInterceptor
import ifx.naming.Naming.isContract
import ifx.service.ServiceBase
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import java.net.ServerSocket
import kotlin.reflect.cast

class Host(port: Int = 0) {
    private val log = KotlinLogging.logger { }
    val port: Int = if (port == 0) randomFreePort() else port
    val builder: ServerBuilder<*> = ServerBuilder.forPort(this.port)
    private lateinit var server: io.grpc.Server


    val serverInterceptors = listOf(
        CourotineContextInterceptor, ExceptionHandler(), ServerLogInterceptor()
    )

    /**
     * Add a service to the host, binding it to the given service contract
     */
    inline fun <reified TContract : Any> addService(instance: TContract): Host = apply {
        val grpc = GrpcServer<TContract>(instance)
        builder.addService(ServerInterceptors.intercept(grpc, serverInterceptors))
    }

    /**
     * Add a service to the host, binding it to each service contract the service implements
     */
    fun addService(instance: ServiceBase): Host = apply {
        val grpcServices = instance
            .facets()
            .map { GrpcServer(it, it.cast(instance)) }
            .map { ServerInterceptors.intercept(it, serverInterceptors) }
        builder.addServices(grpcServices)
    }


    private fun ServiceBase.facets() = this::class.java.interfaces.map { it.kotlin }.filter { it.isContract() }

    fun start(): Host = apply {
        server = builder.build().start()
        log.info { "Starting server on port $port with services: [${server.services.joinToString { it.serviceDescriptor.name }}]" }
        val block = Thread {
            log.info { "*** shutting down gRPC server since JVM is shutting down" }
            this.stop()
            log.info { "*** server shut down" }
        }
        Runtime.getRuntime().addShutdownHook(block)
    }

    fun stop(): Host = apply {
        server.shutdown()
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
