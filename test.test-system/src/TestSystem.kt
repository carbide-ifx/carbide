import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import engine.pricing.contract.IPricingEngine
import engine.pricing.service.PricingEngine
import ifx.host.Host
import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import ifx.logging.Log
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.proxy.factory.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.subsystem.default
import ifx.telemetry.otel.BatchSpanProcessor
import ifx.telemetry.otel.OpenTelemetryInterceptor
import ifx.telemetry.otel.OtlpHttpSpanExporter
import kotlinx.coroutines.runBlocking
import manager.sales.contract.ISalesManager
import manager.sales.service.SalesManager

suspend fun startTestSystem(
    interceptors: List<IInterceptor> = listOf(LoggingInterceptor()),
): IHost {
    val host = Host.default(
        name = "Test System",
        interceptors = interceptors,
    )
    // The services below hold this factory, so its connections live exactly as long as the host.
    val proxyFactory = RSocketProxyFactory.forHost(host)
    host.onStop { proxyFactory.close() }

    host.registerService<IProductAccess> { ProductAccessEmulator().apply { seedTestData() } }
        .registerService<IPricingEngine> { PricingEngine(proxyFactory) }
        .registerService<ISalesManager> { SalesManager(proxyFactory) }
        .start()

    return host
}

fun main(): Unit = runBlocking {
    val telemetryExporter = OtlpHttpSpanExporter(endpoint = "http://localhost:4318/v1/traces")
    val telemetryProcessor = BatchSpanProcessor(
        exporter = telemetryExporter,
        onDroppedSpans = { dropped ->
            Log("OpenTelemetry").warn {
                "Dropped ${dropped.count} spans (${dropped.reason}): ${dropped.cause?.message}"
            }
        },
    )
    val telemetry = OpenTelemetryInterceptor(
        spanProcessor = telemetryProcessor,
        serviceName = "test-system",
    )
    val system = startTestSystem(
        interceptors = listOf(LoggingInterceptor(), telemetry),
    )
    val proxyFactory = RSocketProxyFactory.forHost(system)

    try {
        proxyFactory.create<ISalesManager>().listProducts().collect {
            Log("in main").info { it }
        }

        Log.info { "press any key to close" }
        readln()
    } finally {
        proxyFactory.close()
        system.stop()
        telemetryProcessor.shutdown()
    }
}
