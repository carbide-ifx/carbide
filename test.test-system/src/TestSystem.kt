import access.product.contract.IProductAccess
import access.product.service.ProductAccessEmulator
import engine.pricing.contract.IPricingEngine
import engine.pricing.service.PricingEngine
import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import ifx.host.rsocket.Host
import ifx.logging.Log
import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.interceptors.Encryption
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import ifx.telemetry.otel.OpenTelemetryInterceptor
import ifx.telemetry.otel.OtlpHttpSpanExporter
import kotlinx.coroutines.runBlocking
import manager.sales.contract.ISalesManager
import manager.sales.service.SalesManager


suspend fun startTestSystem(
    interceptors: List<IInterceptor> = listOf(LoggingInterceptor(), Encryption),
): IHost {
    val host = Host(0, "Test System")
        .addInterceptors(interceptors)
    val proxyFactory = ProxyFactory.forHost(host)

    host.registerService<IProductAccess> { ProductAccessEmulator().apply { seedTestData() } }
        .registerService<IPricingEngine> { PricingEngine(proxyFactory) }
        .registerService<ISalesManager> { SalesManager(proxyFactory) }
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
    val system = startTestSystem(listOf(LoggingInterceptor(), telemetry, Encryption))
    val proxyFactory = ProxyFactory.forHost(system)

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
