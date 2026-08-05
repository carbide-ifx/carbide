package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServerInterceptorPipeline
import ifx.protocol.contract.serviceDescriptorOf
import ifx.service.IService
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class Host(
    private val listeners: List<ProtocolListener>,
    val name: String = "Service Host",
    override val interceptors: MutableList<IInterceptor> = mutableListOf(),
) : IHost {
    private val endpoints = mutableListOf<Endpoint>()
    private val runningServers = mutableListOf<RunningServer>()

    override var boundListeners: List<BoundProtocolListener> = requestedListeners()
        private set

    init {
        require(listeners.isNotEmpty()) { "A host must have at least one protocol listener" }
        val duplicateIds = listeners.groupingBy { it.protocol.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "A protocol may only be exposed once per host: ${duplicateIds.joinToString()}"
        }
        val duplicatePorts = listeners
            .filter { it.port != 0 }
            .groupingBy { it.port }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicatePorts.isEmpty()) {
            "Each protocol listener must use a separate port: ${duplicatePorts.joinToString()}"
        }
        require(listeners.count { it.tooling != null } <= 1) {
            "Host tooling may only be installed on one protocol listener"
        }
    }

    override suspend fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost = apply {
        check(runningServers.isEmpty()) { "Services cannot be registered while the host is open" }
        val descriptor = serviceDescriptorOf(contract)
        val serviceBinding = descriptor.bind(instance)
        val interceptorBinding: IBinding =
            ServerInterceptorPipeline(descriptor.address, interceptors, serviceBinding)
        endpoints += Endpoint(descriptor.address, interceptorBinding, descriptor.description)
    }

    override suspend fun <T : IService> registerService(contract: KClass<T>, factory: suspend () -> T): IHost =
        registerService(contract, factory())

    override fun addInterceptors(vararg i: IInterceptor): IHost = apply {
        check(endpoints.isEmpty()) { "Interceptors must be added before services are registered" }
        interceptors.addAll(i)
    }

    override fun addInterceptors(interceptors: List<IInterceptor>): IHost = apply {
        check(endpoints.isEmpty()) { "Interceptors must be added before services are registered" }
        this.interceptors.addAll(interceptors)
    }

    override fun open(): IHost = apply {
        check(runningServers.isEmpty()) { "Host is already open" }
        val started = mutableListOf<RunningServer>()
        try {
            listeners.forEach { config ->
                val running = createServer(config)
                running.port = running.start()
                started += running
            }
            runningServers += started
            boundListeners = started.map { running ->
                BoundProtocolListener(running.config.protocol.id, running.config.host, running.port)
            }
        } catch (exception: Throwable) {
            started.asReversed().forEach { it.stop() }
            throw exception
        }
    }

    override fun close(): IHost = apply {
        runningServers.asReversed().forEach { it.stop() }
        runningServers.clear()
        boundListeners = requestedListeners()
    }

    private fun createServer(config: ProtocolListener): RunningServer {
        val server = embeddedServer(
            factory = CIO,
            environment = applicationEnvironment {
                log = kermitKtorLogger("Ktor.${config.protocol.id}")
            },
            configure = {
                connectors.add(EngineConnectorBuilder().apply {
                    host = config.host
                    port = config.port
                })
            },
        ) {
            config.protocol.install(this, endpoints)
            config.tooling?.let {
                installHostTooling(name, endpoints, it) { boundListeners }
            }
        }
        return RunningServer(
            config = config,
            startServer = {
                server.start()
                runBlocking { server.engine.resolvedConnectors().single().port }
            },
            stopServer = { server.stop() },
        )
    }

    private fun requestedListeners(): List<BoundProtocolListener> = listeners.map { config ->
        BoundProtocolListener(config.protocol.id, config.host, config.port)
    }

    private class RunningServer(
        val config: ProtocolListener,
        val startServer: () -> Int,
        val stopServer: () -> Unit,
    ) {
        var port: Int = config.port
        fun start(): Int = startServer()
        fun stop(): Unit = stopServer()
    }
}
