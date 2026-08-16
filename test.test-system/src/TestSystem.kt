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
import ifx.proxy.contract.create
import ifx.proxy.factory.RSocketProxyFactory
import ifx.subsystem.default
import ifx.telemetry.otel.OpenTelemetryInterceptor
import ifx.telemetry.otel.OtlpHttpSpanExporter
import kotlinx.coroutines.runBlocking
import manager.sales.contract.ISalesManager
import manager.sales.service.SalesManager

internal const val SERVICE_EXPLORER_DIRECTORY: String = "../typescript/ifx-test-ui/dist"
internal const val SERVICE_EXPLORER_DIRECTORY_ENV: String = "IFX_SERVICE_EXPLORER_DIRECTORY"

internal fun serviceExplorerDirectory(environment: Map<String, String>): String =
    environment[SERVICE_EXPLORER_DIRECTORY_ENV]
        ?.takeIf(String::isNotBlank)
        ?: SERVICE_EXPLORER_DIRECTORY

suspend fun startTestSystem(
    interceptors: List<IInterceptor> = listOf(LoggingInterceptor()),
    serviceExplorerDirectory: String? = null,
): IHost {
    val host = Host.default(
        name = "Test System",
        interceptors = interceptors,
        serviceExplorerDirectory = serviceExplorerDirectory,
    )
    // The services below hold this factory, so its connections live exactly as long as the host.
    val proxyFactory = RSocketProxyFactory.forHost(host)
    host.onClose { proxyFactory.close() }

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
    val system = startTestSystem(
        interceptors = listOf(LoggingInterceptor(), telemetry),
        serviceExplorerDirectory = serviceExplorerDirectory(System.getenv()),
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
        system.close()
        telemetryExporter.close()
    }
}
