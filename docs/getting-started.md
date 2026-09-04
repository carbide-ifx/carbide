# Getting started with Carbide

This walkthrough creates one contract, hosts an implementation, and calls it through a typed proxy.
It uses the standard subsystem composition. The examples assume Carbide version `0.1.0`, matching the
current repository configuration.

## 1. Create a contract module

The contract module needs the service API and the contract-index processor:

```yaml
product:
  type: lib
  platforms: [jvm, macosArm64]

settings:
  kotlin:
    ksp:
      processors:
        - io.carbide-ifx:ifx.contract.ksp:0.1.0

dependencies:
  - io.carbide-ifx:ifx.service:0.1.0
```

Define an interface extending `IService`. Request and response models that cross the wire must be
serializable:

```kotlin
package example.greeter.contract

import ifx.service.IService
import kotlinx.serialization.Serializable

interface IGreeter : IService {
    suspend fun greet(request: GreetRequest): GreetResponse
}

@Serializable
data class GreetRequest(val name: String)

@Serializable
data class GreetResponse(val message: String)
```

The processor records the contract in the module's generated index. It does not expose the service
or generate a transport-specific implementation.

## 2. Create a subsystem application

The subsystem depends on the contract and the runtime bundle. It applies the subsystem processor and
RPC compiler plugin:

```yaml
product:
  type: jvm/app

settings:
  jvm:
    mainClass: example.greeter.GreeterSystemKt
  kotlin:
    ksp:
      processors:
        - io.carbide-ifx:ifx.subsystem.ksp:0.1.0
    compilerPlugins:
      - id: ifx.rpc.compiler
        dependency: io.carbide-ifx:ifx.rpc.compiler:0.1.0

dependencies:
  - ../greeter.contract
  - io.carbide-ifx:ifx.subsystem:0.1.0
```

`ifx.subsystem` is the single normal runtime dependency. It exports the host, protocols, proxy
factories, context, logging, telemetry, actuator, and Service Explorer APIs.

## 3. Implement and register the service

```kotlin
package example.greeter

import example.greeter.contract.GreetRequest
import example.greeter.contract.GreetResponse
import example.greeter.contract.IGreeter
import ifx.host.Host
import ifx.host.IHost.Companion.registerService
import ifx.proxy.factory.create
import ifx.protocol.rsocket.RSocketProxyFactory
import ifx.subsystem.development
import kotlinx.coroutines.runBlocking

class Greeter : IGreeter {
    override suspend fun greet(request: GreetRequest) =
        GreetResponse("Hello, ${request.name}")
}

fun main(): Unit = runBlocking {
    val host = Host.development(
        name = "Greeter System",
        rsocketPort = 7000,
        jsonRpcPort = 7001,
    )
    host.registerService<IGreeter> { Greeter() }.start()

    val clients = RSocketProxyFactory.forHost(host)
    try {
        val greeter = clients.create<IGreeter>()
        println(greeter.greet(GreetRequest("Ada")).message)
        readln()
    } finally {
        clients.close()
        host.stop()
    }
}
```

`Host.development()` returns an unstarted host. Register all interceptors and services before `start()`.
The example fixes the ports for clarity; passing `0` asks the operating system for a free port.
The development host exposes unauthenticated protocols, actuator logs, and Service Explorer.
For production, construct `Host` directly and install only the listeners and utilities you intend to expose.

The compiler plugin supplies the generated descriptor to `registerService<IGreeter>()` and
`create<IGreeter>()`. Code built without the plugin can call the low-level overloads with the
generated descriptor explicitly.

## 4. Choose the interaction shape

Carbide derives the interaction from the Kotlin signature:

```kotlin
interface IExample : IService {
    @FireAndForget
    suspend fun notify(request: Notice)

    suspend fun find(request: FindRequest): Result

    fun watch(request: WatchRequest): Flow<Result>
}
```

| Kotlin contract | Interaction | RSocket | JSON-RPC |
| --- | --- | --- | --- |
| `@FireAndForget suspend fun ...` | Fire-and-forget | Yes | Notification |
| `suspend fun ...: T` | Request/response | Yes | Yes |
| `fun ...: Flow<T>` | Request stream | Yes | No |

Choose RSocket when a contract streams results. JSON-RPC calls to streaming operations fail
explicitly.

## 5. Call another service from an implementation

Create one proxy factory at the subsystem composition root and share it with services that have
dependencies:

```kotlin
val clients = RSocketProxyFactory.forHost(host)
host.onStop { clients.close() }

host.registerService<IProductAccess> { ProductAccess(database) }
    .registerService<ISalesManager> { SalesManager(clients) }
    .start()

class SalesManager(private val clients: IProxyFactory) : ISalesManager {
    private val products get() = clients.create<IProductAccess>()

    override fun listProducts(): Flow<Product> = TODO()
}
```

The factory owns and caches connections; do not create one per invocation. To call a service on a
different subsystem, bind a view to its endpoint:

```kotlin
val products = clients
    .at(ServiceEndpoint("product-service.internal", 8080))
    .create<IProductAccess>()
```

Keep endpoint selection in the composition root when deployment topology is static.

## 6. Add policies and observability

Add application interceptors to the host. `RSocketProxyFactory.forHost(host)` mirrors the host's
client-safe interceptors automatically. `Host.development()` always adds context propagation and
unhandled-exception reporting itself:

```kotlin
val spanProcessor = BatchSpanProcessor(
    exporter = OtlpHttpSpanExporter("http://localhost:4318/v1/traces"),
)
val rpcMetrics = RpcMetrics(
    exporter = OtlpHttpMetricExporter("http://localhost:4318/v1/metrics"),
)
val telemetry = TelemetryRuntime(
    spanProcessor = spanProcessor,
    resource = TelemetryResource(
        serviceName = "greeter-system",
        serviceNamespace = "examples",
        serviceVersion = "1.0.0",
        serviceInstanceId = instanceId,
        deploymentEnvironmentName = "development",
    ),
    rpcMetrics = rpcMetrics,
)
val interceptors = listOf(telemetry.rpcInterceptor(logRpcCalls = true))

val host = Host.development(interceptors = interceptors)
val clients = RSocketProxyFactory.forHost(host)
```

Tracing defaults to `ParentBasedSampler(AlwaysOnSampler)`. Use
`ParentBasedSampler(ProbabilitySampler(0.1))` to sample ten percent of new traces while retaining
the sampling decision received from an upstream caller.

With `logRpcCalls = true`, the same interceptor emits request and response logs whose structured
tags include `trace_id`, `span_id`, and `trace_flags`. The suspending `Log` severity methods attach
the active RPC correlation to ordinary application calls such as `log.info { "Loading products" }`.
RPC diagnostics remain console-only; actuator log tails retain service application logs.

Manual work uses the same tracer and therefore becomes a child of the current RPC span:

```kotlin
telemetry.tracer.span("load-products") {
    repository.loadProducts()
}
```

For asynchronous or many-to-many causality, link the new span to the contexts that caused it:

```kotlin
telemetry.tracer.span(
    name = "process orders",
    kind = SpanKind.CONSUMER,
    links = listOf(SpanLink(messageCreationContext)),
) {
    process(messages)
}
```

Creation-time links are visible to the sampler. Use `addLink(...)` when a relationship is only
discovered after the span starts. The default limit is 128 retained links per span; configure
`TelemetryRuntime(maxLinksPerSpan = ...)` to change it. Excess links are reported as dropped rather
than retained without bound.

Use `flow.inSpan(telemetry.tracer, "stream-products")` for lazy flows. The application lifecycle must
call suspending `telemetry.shutdown()` after stopping the host; this drains queued spans, exports the
final cumulative RPC histograms, and closes both exporters. Use `telemetry.flush()` when current
telemetry must be exported without stopping.

For ordinary HTTP clients, install `OpenTelemetryClientPlugin` from `ifx.telemetry.otel` with
`tracer = telemetry.tracer`. The plugin creates client spans and
injects `traceparent` / `tracestate`; keep it off the OTLP exporter's own client. Its spans end after
response headers are received and do not include later response-body consumption.

`TelemetryResource` also accepts application resource attributes. Its typed identity fields take
precedence if the same standard key appears in that map.

The development host also registers `IActuator`, browser Service Explorer, and health endpoints on its
RSocket listener:

```text
/ifx/health/ready
/ifx/health/live
/ifx/health
```

The actuator exposes the service catalog, service health, and retained log tails through normal
Carbide RPC calls.

## 7. Build and run this repository's example

The checked-in executable example is `test.test-system`:

```shell
./kotlin build
./kotlin run -m test.test-system
```

Its composition root is `test.test-system/src/TestSystem.kt`; its contracts are in
`test.service-contracts/src`. Use those modules as executable examples when the API reference and a
snippet differ.

## Next steps

- Read the [architecture overview](architecture.md) for component ownership and dependency
  direction, and the [module catalog](module-catalog.md) for what each module owns.
- See the root [README](../README.md) for custom hosts, endpoint projections, gateways, TypeScript
  generation, container images, context propagation, tracing, and proxy lifetime details.
