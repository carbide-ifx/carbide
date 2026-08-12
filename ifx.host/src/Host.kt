package ifx.host

import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.ServerInterceptorPipeline
import ifx.protocol.contract.ServiceDescriptor
import ifx.service.IService
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking

class Host(
    private val listeners: List<ProtocolListener>,
    val name: String = "Service Host",
    override val interceptors: MutableList<IInterceptor> = mutableListOf(),
    private val extensions: List<HostExtension> = emptyList(),
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
        val missingExtensionListeners = extensions
            .map(HostExtension::listener)
            .filterNot { target -> listeners.any { listener -> listener === target } }
        require(missingExtensionListeners.isEmpty()) {
            "Every host extension must target a listener configured on the host"
        }
    }

    constructor(
        protocol: IServerProtocol,
        vararg additionalProtocols: IServerProtocol,
        name: String = "Service Host",
    ) : this(
        listeners = listOf(protocol, *additionalProtocols).map(::ProtocolListener),
        name = name,
    )

    constructor(
        name: String = "Service Host",
        configure: HostBuilder.() -> Unit,
    ) : this(HostBuilder().apply(configure).build(), name)

    private constructor(
        configuration: HostConfiguration,
        name: String,
    ) : this(
        listeners = configuration.listeners,
        name = name,
        extensions = configuration.extensions,
    )

    override suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        instance: T,
    ): IHost = apply {
        check(runningServers.isEmpty()) { "Services cannot be registered while the host is open" }
        val serviceBinding = descriptor.bind(instance)
        val interceptorBinding: IBinding =
            ServerInterceptorPipeline(descriptor.address, interceptors, serviceBinding)
        endpoints += Endpoint(descriptor.address, interceptorBinding, descriptor.description)
    }

    override suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        factory: suspend () -> T,
    ): IHost = registerService(descriptor, factory())

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
            val context = HostExtensionContext(name, endpoints.toList()) { boundListeners }
            extensions.filter { it.listener === config }.forEach { extension ->
                extension.install(this, context)
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

    companion object
}

class HostBuilder internal constructor() {
    private val listeners = mutableListOf<ProtocolListener>()
    private val extensions = mutableListOf<HostExtension>()

    fun listen(
        protocol: IServerProtocol,
        port: Int = 0,
        host: String = "0.0.0.0",
    ): ProtocolListener = ProtocolListener(protocol, port, host).also(listeners::add)

    fun install(extension: HostExtension) {
        extensions += extension
    }

    internal fun build(): HostConfiguration = HostConfiguration(listeners.toList(), extensions.toList())
}

internal data class HostConfiguration(
    val listeners: List<ProtocolListener>,
    val extensions: List<HostExtension>,
)
