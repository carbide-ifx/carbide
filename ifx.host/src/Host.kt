package ifx.host

import ifx.logging.Log
import ifx.logging.LogTag
import ifx.protocol.contract.Endpoint
import ifx.protocol.contract.IBinding
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.CallDirection
import ifx.protocol.contract.InterceptorPipeline
import ifx.protocol.contract.ProtocolListenerDescription
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescriptor
import ifx.protocol.contract.ServiceKind
import ifx.protocol.contract.interceptors.ContextInterceptor
import ifx.protocol.contract.interceptors.UnhandledExceptionInterceptor
import ifx.service.IService
import ifx.service.IServiceLifecycle
import ifx.service.ServiceHealth
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class Host(
    private val listeners: List<ProtocolListener>,
    override val name: String = "Service Host",
    interceptors: MutableList<IInterceptor> = mutableListOf(),
    private val healthCheckTimeout: Duration = 5.seconds,
    private val drainDelay: Duration = ZERO,
    private val requestDrainTimeout: Duration = 20.seconds,
) : IHost {
    private val endpoints = mutableListOf<Endpoint>()
    private val registeredServices = mutableListOf<RegisteredService>()
    private val startedServiceLifecycles = mutableListOf<IServiceLifecycle>()
    private val runningServers = mutableListOf<RunningServer>()
    private val stopActions = mutableListOf<() -> Unit>()
    private val additionalInterceptors = interceptors
    private val contextInterceptor = ContextInterceptor()
    private val callTracker = HostCallTracker()

    /** Interceptors safe to mirror onto clients, in client registration order. */
    override val interceptors: List<IInterceptor>
        get() = listOf(contextInterceptor) + additionalInterceptors

    override var state: HostState = HostState.NEW
        private set

    override var boundListeners: List<ProtocolListenerDescription> = requestedListeners()
        private set

    init {
        require(listeners.isNotEmpty()) { "A host must have at least one protocol listener" }
        val duplicateIds = listeners.groupingBy(ProtocolListener::id).eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "A listener id may only be used once per host: ${duplicateIds.joinToString()}"
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
    }

    constructor(
        protocol: IServerProtocol,
        vararg additionalProtocols: IServerProtocol,
        name: String = "Service Host",
        healthCheckTimeout: Duration = 5.seconds,
        drainDelay: Duration = ZERO,
        requestDrainTimeout: Duration = 20.seconds,
    ) : this(
        listeners = listOf(protocol, *additionalProtocols).map(::ProtocolListener),
        name = name,
        healthCheckTimeout = healthCheckTimeout,
        drainDelay = drainDelay,
        requestDrainTimeout = requestDrainTimeout,
    )

    constructor(
        name: String = "Service Host",
        healthCheckTimeout: Duration = 5.seconds,
        drainDelay: Duration = ZERO,
        requestDrainTimeout: Duration = 20.seconds,
        configure: HostBuilder.() -> Unit,
    ) : this(
        HostBuilder().apply(configure).build(),
        name,
        healthCheckTimeout,
        drainDelay,
        requestDrainTimeout,
    )

    private constructor(
        configuration: HostConfiguration,
        name: String,
        healthCheckTimeout: Duration,
        drainDelay: Duration,
        requestDrainTimeout: Duration,
    ) : this(
        listeners = configuration.listeners,
        name = name,
        healthCheckTimeout = healthCheckTimeout,
        drainDelay = drainDelay,
        requestDrainTimeout = requestDrainTimeout,
    )

    override suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        instance: T,
    ): IHost = apply {
        check(state == HostState.NEW) { "Services can only be registered before the host starts" }
        val serviceBinding = descriptor.bind(instance)
        val exceptionLog = Log(
            LogTag(
                serviceInterface = descriptor.address,
                serviceClassName = instance::class.qualifiedName,
            ),
        )
        val mandatoryInterceptors = MandatoryInterceptors(
            context = contextInterceptor,
            exception = UnhandledExceptionInterceptor { call, exception ->
                exceptionLog.error(exception, tag = call.operation) {
                    val interaction = call.interactionType.name.lowercase().replace('_', '-')
                    "Unhandled exception in $interaction ${call.operation}"
                }
            },
            lifecycle = HostLifecycleInterceptor(callTracker) { call ->
                descriptor.description.kind == ServiceKind.UTILITY && call.operation == "health()"
            },
        )
        val interceptorBinding: IBinding =
            InterceptorPipeline(
                descriptor.address,
                CallDirection.SERVER,
                mandatoryInterceptors.withAdditional(additionalInterceptors),
                serviceBinding,
            )
        endpoints += Endpoint(interceptorBinding, descriptor.description)
        registeredServices += RegisteredService(descriptor.address, instance as? IServiceLifecycle)
    }

    override suspend fun <T : IService> registerService(
        descriptor: ServiceDescriptor<T>,
        factory: suspend () -> T,
    ): IHost = registerService(descriptor, factory())

    override fun addInterceptors(vararg i: IInterceptor): IHost = apply {
        check(endpoints.isEmpty()) { "Interceptors must be added before services are registered" }
        additionalInterceptors.addAll(i)
    }

    override fun addInterceptors(interceptors: List<IInterceptor>): IHost = apply {
        check(endpoints.isEmpty()) { "Interceptors must be added before services are registered" }
        additionalInterceptors.addAll(interceptors)
    }

    override suspend fun start(): IHost = apply {
        check(state == HostState.NEW) { "Host can only be started once; current state is $state" }
        state = HostState.STARTING
        try {
            registeredServices.mapNotNull(RegisteredService::lifecycle).forEach { lifecycle ->
                lifecycle.start()
                startedServiceLifecycles += lifecycle
            }
            openListeners()
            callTracker.startAccepting()
            state = HostState.READY
        } catch (failure: Throwable) {
            startedServiceLifecycles.asReversed().forEach { lifecycle ->
                runCatching { lifecycle.stop() }.exceptionOrNull()?.let(failure::addSuppressed)
            }
            startedServiceLifecycles.clear()
            state = HostState.FAILED
            throw failure
        }
    }

    private fun openListeners() {
        val started = mutableListOf<RunningServer>()
        try {
            listeners.forEach { config ->
                val running = createServer(config)
                running.port = running.start()
                started += running
            }
            runningServers += started
            boundListeners = started.map { running ->
                ProtocolListenerDescription(
                    protocolId = running.config.protocol.id,
                    host = running.config.host,
                    port = running.port,
                    listenerId = running.config.id,
                )
            }
        } catch (exception: Throwable) {
            started.asReversed().forEach { it.stop() }
            throw exception
        }
    }

    override fun onStop(action: () -> Unit): IHost = apply { stopActions += action }

    override suspend fun stop(): IHost = withContext(NonCancellable) {
        if (state == HostState.STOPPED) return@withContext this@Host
        if (state == HostState.READY) {
            callTracker.beginDrain()
            state = HostState.DRAINING
            delay(drainDelay)
            callTracker.awaitIdle(requestDrainTimeout)
            callTracker.finishDrain()
        }
        state = HostState.STOPPING
        val failures = mutableListOf<Throwable>()
        startedServiceLifecycles.asReversed().forEach { lifecycle ->
            runCatching { lifecycle.stop() }.exceptionOrNull()?.let(failures::add)
        }
        startedServiceLifecycles.clear()

        runningServers.asReversed().forEach { server ->
            runCatching { server.stop() }.exceptionOrNull()?.let(failures::add)
        }
        runningServers.clear()
        boundListeners = requestedListeners()

        stopActions.asReversed().forEach { action ->
            runCatching(action).exceptionOrNull()?.let(failures::add)
        }
        stopActions.clear()
        state = HostState.STOPPED
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
        this@Host
    }

    override suspend fun health(): HostHealth {
        val services = coroutineScope {
            registeredServices.map { service ->
                async { service.healthSnapshot() }
            }.awaitAll()
        }
        return HostHealth(
            state = state,
            ready = state == HostState.READY && services.all(ServiceHealthSnapshot::ready),
            live = state != HostState.FAILED && state != HostState.STOPPED &&
                services.all(ServiceHealthSnapshot::live),
            services = services,
        )
    }

    private suspend fun RegisteredService.healthSnapshot(): ServiceHealthSnapshot {
        val health = lifecycle?.let { lifecycle ->
            try {
                withTimeoutOrNull(healthCheckTimeout) { lifecycle.health() }
                    ?: ServiceHealth(
                        ready = false,
                        live = false,
                        detail = "Health check timed out after ${healthCheckTimeout.inWholeMilliseconds}ms",
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                ServiceHealth(ready = false, live = false, detail = failure.message)
            }
        } ?: ServiceHealth.healthy()
        return ServiceHealthSnapshot(
            serviceInterface = address,
            ready = health.ready,
            live = health.live,
            detail = health.detail,
        )
    }

    override fun serviceCatalog(): ServiceCatalog = ServiceCatalog(
        name = name,
        services = endpoints.map(Endpoint::description),
        listeners = boundListeners,
    )

    private fun createServer(config: ProtocolListener): RunningServer {
        val listenerEndpoints = config.endpointSource.endpoints(endpoints.toList()).toList()
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
            config.protocol.install(this, listenerEndpoints)
            val context = HostExtensionContext(::health)
            config.extensions.forEach { extension -> extension.install(this, context) }
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

    private fun requestedListeners(): List<ProtocolListenerDescription> = listeners.map { config ->
        ProtocolListenerDescription(config.protocol.id, config.host, config.port, config.id)
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

    private data class RegisteredService(
        val address: String,
        val lifecycle: IServiceLifecycle?,
    )

    companion object
}

class HostBuilder internal constructor() {
    private val listeners = mutableListOf<ProtocolListener>()

    fun listen(
        protocol: IServerProtocol,
        port: Int = 0,
        host: String = "0.0.0.0",
        id: String = protocol.id,
        endpointSource: EndpointSource = EndpointSource.Registered,
        configure: ListenerBuilder.() -> Unit = {},
    ): ProtocolListener = ProtocolListener(
        protocol = protocol,
        port = port,
        host = host,
        id = id,
        endpointSource = endpointSource,
        extensions = ListenerBuilder(protocol).apply(configure).build(),
    ).also(listeners::add)

    internal fun build(): HostConfiguration = HostConfiguration(listeners.toList())
}

class ListenerBuilder internal constructor(private val protocol: IServerProtocol) {
    private val extensions = mutableListOf<HostExtension>()

    fun install(extension: HostExtension) {
        val required = extension.requiredProtocolId
        require(required == null || required == protocol.id) {
            "${extension::class.simpleName} requires a $required listener but was installed on ${protocol.id}"
        }
        extensions += extension
    }

    internal fun build(): List<HostExtension> = extensions.toList()
}

internal data class HostConfiguration(
    val listeners: List<ProtocolListener>,
)

/**
 * Mandatory server interceptors straddle caller-supplied interceptors so that context is decoded
 * closest to the service, exceptions are reported outside caller interceptors, and lifecycle
 * admission and in-flight tracking wrap the complete invocation.
 */
private data class MandatoryInterceptors(
    val context: IInterceptor,
    val exception: IInterceptor,
    val lifecycle: IInterceptor,
) {
    fun withAdditional(additional: List<IInterceptor>): List<IInterceptor> =
        listOf(context) + additional + exception + lifecycle
}
