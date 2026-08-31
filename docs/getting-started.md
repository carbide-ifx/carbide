# Getting started with iFX

This walkthrough creates one contract, hosts an implementation, and calls it through a typed proxy.
It uses the standard subsystem composition. The examples assume iFX version `0.0.9`, matching the
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
        - sonat:ifx.contract.ksp:0.0.9

dependencies:
  - sonat:ifx.service:0.0.9
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
        - sonat:ifx.subsystem.ksp:0.0.9
    compilerPlugins:
      - id: ifx.rpc.compiler
        dependency: sonat:ifx-rpc-compiler-plugin:0.0.9

dependencies:
  - ../greeter.contract
  - sonat:ifx.subsystem:0.0.9
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
import ifx.proxy.factory.RSocketProxyFactory
import ifx.subsystem.default
import kotlinx.coroutines.runBlocking

class Greeter : IGreeter {
    override suspend fun greet(request: GreetRequest) =
        GreetResponse("Hello, ${request.name}")
}

fun main(): Unit = runBlocking {
    val host = Host.default(
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

`Host.default()` returns an unstarted host. Register all interceptors and services before `start()`.
The example fixes the ports for clarity; passing `0` asks the operating system for a free port.

The compiler plugin supplies the generated descriptor to `registerService<IGreeter>()` and
`create<IGreeter>()`. Code built without the plugin can call the low-level overloads with the
generated descriptor explicitly.

## 4. Choose the interaction shape

iFX derives the interaction from the Kotlin signature:

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
client-safe interceptors automatically. `Host.default()` always adds context propagation and
unhandled-exception reporting itself:

```kotlin
val telemetry = OpenTelemetryInterceptor(
    exporter = OtlpHttpSpanExporter("http://localhost:4318/v1/traces"),
    serviceName = "greeter-system",
)
val interceptors = listOf(LoggingInterceptor(), telemetry)

val host = Host.default(interceptors = interceptors)
val clients = RSocketProxyFactory.forHost(host)
```

The default host also registers `IActuator`, browser Service Explorer, and health endpoints on its
RSocket listener:

```text
/ifx/health/ready
/ifx/health/live
/ifx/health
```

The actuator exposes the service catalog, service health, and retained log tails through normal
iFX RPC calls.

## 7. Build and run this repository's example

The checked-in executable example is `test.test-system`. A clean source build first needs the
repository's Kotlin 2.4-compatible Terpal plugin in Maven Local:

```shell
./terpal.compiler-plugin/publish-local
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
