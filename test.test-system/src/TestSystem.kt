import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import engine.pricing.contract.IPricingEngine
import engine.pricing.service.PricingEngine
import ifx.actuator.registerActuator
import ifx.generated.TestTestSystemServiceDescriptors
import ifx.host.Host
import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import ifx.host.tooling.ServiceExplorer
import ifx.logging.Log
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.protocol.jsonrpc.JsonRpcServerProtocol
import ifx.protocol.rsocket.RSocketServerProtocol
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.telemetry.otel.OpenTelemetryInterceptor
import ifx.telemetry.otel.OtlpHttpSpanExporter
import kotlinx.coroutines.runBlocking
import manager.sales.contract.ISalesManager
import manager.sales.service.SalesManager


suspend fun startTestSystem(
    interceptors: List<IInterceptor> = listOf(LoggingInterceptor()),
): IHost {
    val host = Host(
        name = "Test System",
        serviceDescriptors = TestTestSystemServiceDescriptors,
    ) {
        val rsocket = listen(RSocketServerProtocol())
        install(ServiceExplorer(rsocket, "typescript/ifx-test-ui/dist"))
        listen(JsonRpcServerProtocol())
    }
        .addInterceptors(interceptors)
    val proxyFactory = RSocketProxyFactory.forHost(host)

    host.registerService<IProductAccess> { ProductAccessEmulator().apply { seedTestData() } }
        .registerService<IPricingEngine> { PricingEngine(proxyFactory) }
        .registerService<ISalesManager> { SalesManager(proxyFactory) }
        .registerActuator()
        .open()

    return host
}

fun main(): Unit = runBlocking {
    val telemetryExporter = OtlpHttpSpanExporter(endpoint = "http://localhost:4318/v1/traces")
    val telemetry = OpenTelemetryInterceptor(
        exporter = telemetryExporter,
        serviceName = "test-system",
        onExportFailure = { error ->
            Log("OpenTelemetry").warn { "Failed to export trace: ${error.message}" }
        },
    )
    val system = startTestSystem(listOf(LoggingInterceptor(), telemetry))
    val proxyFactory = RSocketProxyFactory.forHost(system)

    try {
        proxyFactory.create<ISalesManager>().listProducts().collect {
            Log("in main").info { it }
        }

        Log.info { "press any key to close" }
        readln()
    } finally {
        system.close()
        telemetryExporter.close()
    }
}
