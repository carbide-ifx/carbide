
# iFX.kotlin

iFX is a Kotlin Multiplatform framework for defining typed service contracts, hosting their
implementations, and calling them over interchangeable RPC protocols. Start with:

- [Getting started](docs/getting-started.md) — define, host, and call a service.
- [Architecture overview](docs/architecture.md) — what iFX consists of and how calls flow through it.
- [All architecture documentation](docs/README.md) — module catalog, code generation, gateway,
  design decisions, and diagrams.

The sections below are reference documentation for individual capabilities.

## Building from source

The repository includes a Kotlin 2.4 compatibility build for the Terpal compiler plugin. Amper
resolves third-party compiler plugins from Maven repositories, so bootstrap and publish it to Maven
Local once before a clean build:

```shell
./terpal.compiler-plugin/publish-local
./kotlin build
```

The bootstrap downloads checksum-pinned upstream sources and applies the one-line Kotlin compiler
API compatibility change. See [`terpal.compiler-plugin`](terpal.compiler-plugin/README.md) for its
provenance and why the two build phases are explicit.

## RPC service contracts

An RPC service is just an interface extending `IService` and its implementation:

```kotlin
interface AwesomeService : IService {
    suspend fun awesome(request: AwesomeRequest): AwesomeResponse
}

class AwesomeServiceImpl : AwesomeService {
    override suspend fun awesome(request: AwesomeRequest): AwesomeResponse = TODO()
}

host.registerService<AwesomeService> {
    AwesomeServiceImpl()
}

val client = proxyFactory.create<AwesomeService>()
```

`IService` is only the RPC-contract marker. An implementation that owns resources may also
implement `IServiceLifecycle`; the host calls its suspending `start`, `health`, and `stop` methods
locally. Lifecycle methods are never generated as remotely callable service operations.

## Subsystem dependency

Applications that host a subsystem can use the published `ifx.subsystem` module
as their single iFX runtime dependency:

```yaml
dependencies:
  - sonat:ifx.subsystem:0.0.9
```

It exports the host, RSocket and JSON-RPC protocols and proxy factories,
interceptor contracts, context and logging support, OpenTelemetry, the actuator,
and host tooling such as `ServiceExplorer`. `Host.default()` provides the
standard dual-protocol host with actuator inspection enabled. Applications can
construct `Host` directly when they need a different composition.

Generated service bindings still require the subsystem/application KSP and compiler-plugin
setup described below. Those are build-time tools rather than runtime dependencies.

The bundle supports JVM and macOS ARM64 and publishes platform-correct
multiplatform metadata.

## Container images

Runnable JVM subsystem modules can enable the local `ifx.jib` Amper plugin:

```yaml
product:
  type: jvm/app

settings:
  jvm:
    mainClass: com.example.CustomerSubsystemKt

plugins:
  ifx.jib:
    enabled: true
    image: example/customer-subsystem:dev
    ports: [ 8080, 8081 ]
```

The plugin adds three module tasks:

```shell
./kotlin do jibTar -m customer.subsystem     # cacheable image tar, no Docker daemon
./kotlin do jibDocker -m customer.subsystem  # load the image into the local Docker daemon
./kotlin do jibPush -m customer.subsystem    # push directly to the configured registry
```

The default base is the non-root Java 21 distroless image. `baseImage`,
`jvmArgs`, `tags`, `ports`, `environment`, and `labels` can be overridden per
subsystem. Build outputs such as an npm web application remain ordinary files
and can be copied into the image as their own layer:

```yaml
plugins:
  ifx.jib:
    enabled: true
    image: example/customer-subsystem:dev
    extraDirectories:
      - source: //typescript/customer-ui/dist
        destination: /app/webapps/customer
```

The source directory must already have been produced by its owning build. Jib
tracks its contents as task inputs and copies them verbatim; it does not embed
them in Kotlin sources or JAR resources. Registry push and private-base pulls
use standard Docker credential discovery; set `targetCredentialHelper` or
`baseCredentialHelper` when a named helper is required. Do not put registry
passwords in module configuration. Pin `baseImage` by digest when builds must
remain reproducible across base-image updates.

Kotlin Toolchain currently supports only local custom plugin modules. Downstream
repositories must therefore vendor this small `ifx.jib` module until external
plugin publication is supported.

## Multi-protocol hosting

`Host` owns service registration and the lifecycle of its Ktor servers. Each
configured listener exposes exactly one protocol on its own port. A listener uses
the registered service endpoints by default, or an `EndpointSource` can replace
them with an immutable projection. Protocol implementations only install their
routes and wire handling into the listener provided by the host.

```kotlin
import ifx.subsystem.default

val host = Host.default(
    name = "Example System",
    rsocketPort = 7000,
    jsonRpcPort = 7001,
)

host.registerService<AwesomeService> { AwesomeServiceImpl() }
host.start()

val rsocketClient = RSocketProxyFactory.forHost(host).create<AwesomeService>()
val jsonRpcClient = JsonRpcProxyFactory.forHost(host).create<AwesomeService>()
```

Using separate Ktor applications prevents route and plugin collisions between
protocols and gives each listener an independent port and network interface,
with a boundary for protocol-specific TLS and authentication. A listener
configured with `port = 0` receives an available port at startup; use
`host.port(RSOCKET_PROTOCOL_ID)` or
`host.port(JSON_RPC_PROTOCOL_ID)` to read the resolved value.

RSocket supports fire-and-forget, request/response, and request-stream
interactions. Regular JSON-RPC over HTTP supports fire-and-forget notifications
and request/response. Calling a service operation that returns `Flow` through the
JSON-RPC client fails explicitly because JSON-RPC has no standard streaming
interaction.

## API gateway projections

A gateway is a static, transport-neutral projection of existing service
operations. Generated service descriptors expose owner-typed operation values,
so the common case contains no routes, DTO mapping, annotations, or duplicate
interfaces:

```kotlin
val ProductWebApi = gateway("product-web") {
    expose(IProductAccessDescriptor) {
        only(filter, generateRandowProduct)
    }
    expose(ISalesManagerDescriptor)
}
```

`expose(descriptor)` includes all ordinary operations; inherited service
lifecycle operations are excluded. `only(...)` narrows the set. Service names,
operation names, and the surface address follow conventions and can be versioned
or explicitly renamed in the projection.

For an embedded gateway, give the same endpoint source to each public listener.
Internal listeners can continue to use the complete registered endpoint set:

```kotlin
val publicEndpoints = ProductWebApi.endpointSource()
val host = Host {
    listen(RSocketServerProtocol(), id = "internal-rsocket")
    listen(
        RSocketServerProtocol(rSocketAuthenticator),
        id = "public-rsocket",
        endpointSource = publicEndpoints,
    )
    listen(
        GatewayHttpServerProtocol(httpAuthenticator),
        endpointSource = publicEndpoints,
    )
}
```

For a separate gateway process, keep the projection unchanged and supply typed
remote targets:

```kotlin
val publicEndpoints = ProductWebApi.endpointSource {
    remote(IProductAccessDescriptor, productRSocketClient)
    remote(ISalesManagerDescriptor, salesRSocketClient)
}
```

The public RSocket surface is one service address (`product-web` above), with
routes such as `productAccess/filter`. Setup authentication establishes trusted
context for the connection and overwrites any client-supplied context. The
conventional HTTP adapter publishes
`POST /api/{surface}/{manager}/{operation}`; request streams use newline-delimited JSON
events named `next`, `complete`, and `error`. It is deliberately separate from
JSON-RPC.

OpenAPI 3.1 is served at `/api/{surface}/openapi.json` and can also be emitted as
a build artifact without starting a host. Enable the artifact plugin on the
module that declares the projections (the module must already run
`ifx.subsystem.ksp`):

```yaml
plugins:
  ifx.gateway.artifacts:
    enabled: true
```

Then run:

```shell
./kotlin do gatewayArtifacts -m product.gateway
```

KSP finds every non-private top-level `val` whose inferred type is
`GatewayProjection`; no annotation or projection-name string is needed. The
task writes one deterministic directory per public address:

```text
gateway/
├── product-web/
│   ├── sdk.ts
│   └── openapi.json
└── product-web/v2/
    ├── sdk.ts
    └── openapi.json
```

Only indexes generated into the declaring module's own JAR are loaded, so a
gateway does not accidentally publish projections from its dependencies. The
resulting directory is the build/publishing boundary: npm and API-catalog jobs
consume it without loading a host or duplicating the DSL in build
configuration.

The renderers remain directly available when deployment metadata must be
supplied programmatically:

```kotlin
val openApiJson = ProductWebApi.renderOpenApi(
    deployment = GatewayHttpDeployment(
        title = "Product Web API",
        apiVersion = "1.0.0",
        serverUrls = listOf("https://api.example.com"),
    ),
)
```

`renderTypeScriptSdk()` generates a protocol-neutral SDK with manager
namespaces and only the projected operations while preserving the generated DTO
shapes. Use it with either `@ifx/rpc-sdk-rsocket` or the separate
`@ifx/rpc-sdk-http` binding. The latter accepts ordinary Fetch request headers
for browser authentication and decodes NDJSON incrementally.

The `ifx.subsystem` bundle provides an opinionated default host with RSocket,
JSON-RPC, `IActuator`, and the browser Service Explorer. Passing `0` for either
port selects an available port. Because registering the actuator is suspending,
`Host.default()` must be called from a coroutine:

```kotlin
import ifx.subsystem.default

val host = Host.default(rsocketPort = 8080, jsonRpcPort = 8081)
val testHost = Host.default()
```

Every `Host` installs context propagation and unhandled-exception reporting as
mandatory interceptors. Passing `interceptors` only adds caller-defined layers;
it cannot replace the mandatory interceptors. Additional interceptors are
installed before the actuator and subsequent business services:

```kotlin
val host = Host.default(
    interceptors = listOf(LoggingInterceptor(), telemetry),
)
```

For a custom protocol or tooling composition, construct `Host` directly.

### Proxy factory lifetime

A proxy factory owns the client transport, so it is a long-lived object: hold one
per subsystem rather than creating one per call. Each factory keeps a single
binding — and therefore a single connection — per destination and service address, so
`create<T>()` is cheap and repeatable. This is what makes the common manager
shape safe:

```kotlin
class SalesManager(val proxyFactory: IProxyFactory) : ISalesManager {
    val productAccess get() = proxyFactory.create<IProductAccess>()
}
```

When a dependency lives on another host, bind a lightweight view of the factory to that
destination. The view shares the factory's transport, interceptors, connection cache, and
lifecycle:

```kotlin
val productAccess = proxyFactory
    .at(ServiceEndpoint("product-service.internal", 8081))
    .create<IProductAccess>()
```

Bindings are cached by destination and service address, so repeated `at(endpoint).create<T>()`
calls do not create additional transport clients or connections. They remain cached until the
factory closes, so the set of destinations should be stable and bounded. Keep endpoint construction
in the composition root when deployment configuration is static.

Close the factory during shutdown to release its connections:

```kotlin
try {
    // serve requests
} finally {
    proxyFactory.close()
    host.stop()
}
```

Proxies remain valid objects after `close()` but cannot make calls. A connection
that drops is replaced on the next call, so a factory survives a restart of the
service it points at. A failed call is never replayed.

Only acquiring a connection is bounded by a client-side timeout. Calls themselves
are not: an application deadline belongs to the caller, so wrap calls in
`withTimeout` when one is required. Errors raised by a remote service travel as
per-stream error frames and leave the shared connection intact.

Because calls carry no timeout of their own, the RSocket keep-alive is what
detects a peer that stops responding, and it therefore sets the worst-case delay
before a lost connection is noticed. The protocol default of a 20 s interval and
90 s lifetime means a call can wait roughly 110 s. Tighten it per factory when
that is too slow:

```kotlin
val proxyFactory = RSocketProxyFactory.forHost(
    host,
    keepAlive = KeepAlive(interval = 2.seconds, maxLifetime = 6.seconds),
)
```

Every failure — a dropped transport, a remote service exception, an exhausted
connect budget — reaches the caller as `ProtocolException` with the underlying
cause attached, matching the JSON-RPC client. Cancelling the caller is passed
through as cancellation, so `withTimeout` and structured concurrency behave
normally.

## Interceptors

An interceptor is one onion layer around a complete RPC invocation. The invocation
is represented as a cold `Flow<Message>`: fire-and-forget emits nothing,
request/response emits once, and request streams emit normally. Keeping one model
for all three interaction types means cleanup, failures, cancellation, and future
telemetry spans can surround the full lifetime of a stream.

```kotlin
class TimingInterceptor : IInterceptor {
    override fun intercept(
        call: InterceptorCall,
        next: InterceptorChain,
    ): Flow<Message> = flow {
        val started = TimeSource.Monotonic.markNow()
        try {
            emitAll(next(call))
        } finally {
            println("${call.operation}: ${started.elapsedNow()}")
        }
    }
}
```

Client interceptors run in registration order around the transport. Server
interceptors run in reverse order, so using `[logging, encryption]` on both sides
produces a symmetric onion:

```text
client logging -> client encryption -> transport -> server encryption -> server logging -> service
```

`Context` is an immutable ambient container with no predefined application
fields. Every value placed in it is serialized immediately and propagated by
the host's mandatory `ContextInterceptor`:

```kotlin
@Serializable
@SerialName("ifx.caller")
data class Caller(val subject: String)

@Serializable
@SerialName("ifx.request")
data class RequestMetadata(val requestId: String)

val interceptors = listOf(
    LoggingInterceptor(),
    Encryption,
)

host.addInterceptors(interceptors)
proxyFactory.addInterceptors(host.interceptors)

withContext(Context().set(Caller("user-42"))) {
    client.awesome(request)
}
```

On the server, propagated values are installed in the coroutine context
for the complete invocation, including stream collection, and can be read with
`Context.current().getOrNull<Caller>()`. Context values must be serializable;
use a stable `@SerialName` as their cross-system identity. Unknown values remain
as opaque JSON and can pass through systems that do not understand them. The
host places context before caller interceptors in client order, so reversed
server ordering decrypts or decodes headers before context extraction. Generic
JSON headers can be inspected or changed with `Message.headers()` and
`Message.withHeader(...)`.

## Log tail actuator

Service application logs can carry their generated service identity through
Kermit's string tag while retaining a readable console tag:

```kotlin
class AwesomeServiceImpl : AwesomeService {
    private val log = Log.forService<AwesomeService>(this)
    private val repositoryLog = log.withTag("Repository")

    override suspend fun awesome(request: AwesomeRequest): AwesomeResponse {
        repositoryLog.info { "Loading $request" }
        // ...
    }
}
```

The standard writer renders this as `AwesomeServiceImpl.Repository`. The log-tail
writer retains the structured contract address, implementation class, tag path,
severity, message, and throwable. Plain framework log tags continue to reach the
standard writer but are not retained. `LogTail.logs(address)` returns the latest
500 entries for that service address in sequence order. `LogTail.latest(address)`
exposes the retained tail and future entries as a non-blocking
`Flow<LogTailEntry>`. The writer, retention store, and entry model belong to the
`ifx.actuator` module; `ifx.logging` only provides structured tags and the generic
writer installation point.

Every hosted service also reports non-cancellation exceptions that escape its
server invocation. The error log carries the service interface, implementation
class, and operation as its structured path before the original exception is
re-thrown to the active transport. Exception reporting never replaces the
original RPC failure or changes the transport's error response.

Register the separate actuator service to expose that flow through the normal
service transport. Callers use the generated actuator client or an iFX proxy;
there is no separate HTTP streaming endpoint:

```kotlin
host.registerActuator()

val actuator = proxyFactory.create<IActuator>()
val catalog = actuator.catalog()
actuator.logTail<AwesomeService>().collect { entry ->
    println(entry.message)
}
```

## OpenTelemetry traces

`ifx.telemetry.otel` provides tracing without depending on a platform-specific
OpenTelemetry SDK. It propagates W3C `traceparent`/`tracestate` headers and exports
OTLP/HTTP JSON through Ktor on JVM and macOS.

```kotlin
val exporter = OtlpHttpSpanExporter(
    endpoint = "http://localhost:4318/v1/traces",
)
val telemetry = OpenTelemetryInterceptor(
    exporter = exporter,
    serviceName = "sales-manager",
)
val interceptors = listOf(
    LoggingInterceptor(),
    telemetry,
    Encryption,
)

host.addInterceptors(interceptors)
proxyFactory.addInterceptors(interceptors)
```

Place telemetry before interceptors that encode or encrypt message headers. The
server reverses the list, so the same ordering decrypts `traceparent` before the
telemetry layer extracts it.

The HTTP exporter sends each completed span immediately and never fails the RPC
when export fails. Supply `onExportFailure` to the interceptor for diagnostics,
or implement `SpanExporter` to add batching and retry policy. Call
`OtlpHttpSpanExporter.close()` when the application shuts down.

Service modules do not generate descriptors or proxies. Modules that declare interfaces
inheriting `IService` apply the index processor, which generates only a small contract
index:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - sonat:ifx.contract.ksp:0.0.9
```

A subsystem's KSP run reads the reachable contract indexes and generates one Kotlin
descriptor and proxy beside each contract name. No descriptor or proxy is generated in
the service module, and no aggregate runtime registry is generated.

Only subsystem/application modules apply the RPC generator and compiler plugin:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - sonat:ifx.subsystem.ksp:0.0.9
        # Optional: generate TypeScript contracts and wire types.
        - sonat:ifx.rpc.typescript.ksp:0.0.9
    compilerPlugins:
      - id: ifx.rpc.compiler
        dependency: sonat:ifx-rpc-compiler-plugin:0.0.9
```

The subsystem dependency graph is the contract manifest. Contract modules depend only
on `ifx.service` at runtime; they do not generate RPC bindings or depend on protocol code.
Adding or removing a service-module dependency changes the generated descriptors without a
second service list or annotation. Apply the index processor only to modules that declare
service contracts; subsystem and unrelated infrastructure modules do not need it.

`Host.default` is the standard application factory from `ifx.subsystem`. The compiler
plugin supplies its generated `IActuator` descriptor and rewrites typed `registerService<T>` and
`IProxyFactory.create<T>` calls to pass the matching generated `ServiceDescriptor<T>`
directly. Proxy factories created with `forHost` obtain only the host address and
interceptors; descriptor selection remains compile-time:

```kotlin
import ifx.subsystem.default

val host = Host.default(name = "Test System")

host.registerService<IProductAccess> { ProductAccessEmulator() }
```

Reusable helpers can accept a defaulted `ServiceDescriptor<T>` parameter. The compiler
plugin fills that argument in the consuming subsystem, which is how `Host.default()` and
`registerActuator()` remain usable while actuator itself continues to generate only a
contract index.

Code compiled without the plugin can use the low-level APIs by passing a generated
descriptor explicitly:

```kotlin
host.registerService(IProductAccessDescriptor) { ProductAccessEmulator() }
val client = proxyFactory.create(IProductAccessDescriptor)
```

In a multiplatform application, KSP emits individual descriptors into each platform source
set and the compiler plugin links them directly on JVM and Native. Keep host assembly in
the corresponding platform source sets. On Native, the processor first generates an empty
package anchor, then discovers dependency KLIB indexes in the following KSP round. This
keeps dependency aggregation automatic without an explicit contract list.

Generation does not expose a contract. Only `registerService` publishes an endpoint.
Descriptor linking uses no runtime lookup, classpath scanning, reflection, or
associated-object mutation of dependency contracts.

The optional `ifx.rpc.typescript.ksp` processor generates a TypeScript service
interface, operation request/response aliases, and all reachable serializable
types. User-defined request and response types must use `@Serializable` and
custom or contextual serializers are rejected because their wire shape cannot
be inferred from KSP symbols.

## Webapp hosting and interactive service explorer

The `ifx.host.webapp` module provides a general `WebApp` host extension for
mounting a built web application directory on any listener. The web build remains
an ordinary npm, Vite, esbuild, or other frontend build; this extension only serves
its output and does not know about RPC services or tooling.

```kotlin
val host = Host(name = "Example") {
    listen(RSocketServerProtocol(), port = 8080) {
        install(WebApp(directory = "webapp/dist"))
    }
}
```

The `ifx.service-explorer` module bundles the Service Explorer's npm build. The
explorer targets an RSocket listener because its browser client invokes services
and streams logs through RSocket. `Host.default()` always installs it; callers do
not configure or package a frontend directory. Custom hosts can install
`ServiceExplorer` directly. The landing page obtains the host catalog and
per-service health from the registered `IActuator` utility service; no separate
HTTP catalog endpoint is exposed. Selecting a component opens its operations,
generates request controls from the serialized wire types, and displays
request/response, fire-and-forget, and streaming results. The standard host also
publishes Kubernetes-compatible JSON probes at `/ifx/health/ready`,
`/ifx/health/live`, and `/ifx/health` on its RSocket HTTP listener.
Set `drainDelay` on `Host.default()` to the deployment's endpoint-propagation window; it defaults
to zero so local shutdown is immediate. `requestDrainTimeout` bounds how long shutdown waits for
accepted calls and streams before stopping services and listeners.

For example, a host resolved to port `8080` exposes the UI at
`http://localhost:8080/`. The webapp calls `IActuator.catalog()` through the
ordinary generated service SDK.

```kotlin
val host = Host.default(
    name = "Test System",
    rsocketPort = 8080,
)
```

The frontend build is published inside `ifx.service-explorer` for JVM and Native.
JVM uses ordinary JAR resources; Native uses a generated compressed asset
projection because Native library resources require application-level packaging.
Running the frontend build updates these projections and its local `dist/`
directory.

Generated TypeScript contracts export a `{Service}Description` value in
addition to their typed SDK. It contains the same operations and runtime
wire-type schema used by the hosted explorer, so other development tools can
reuse the metadata without attempting to reflect on erased TypeScript types.

Each generated contract also contains a protocol-neutral concrete
`{Service}Sdk`. Choose a separate protocol package when connecting it. The
protocol SDK entrypoint appends the generated service address to its base URL,
while the generated service SDK sends the exact Kotlin operation signatures
through the selected binding:

```typescript
import { RSocketSdk } from "@ifx/rpc-sdk-rsocket";
import { JsonRpcSdk } from "@ifx/rpc-sdk-jsonrpc";
import { ISalesManagerSdk } from "./generated/ISalesManager";

const streamingSdk = await RSocketSdk.connect(
    ISalesManagerSdk,
    "ws://localhost:7000",
);
const jsonRpcSdk = await JsonRpcSdk.connect(
    ISalesManagerSdk,
    "http://localhost:7001",
);

try {
  for await (const product of streamingSdk.listProducts()) {
    console.log(product)
  }
} finally {
  streamingSdk.close()
  jsonRpcSdk.close()
}
```

`@ifx/rpc-sdk` contains only the shared binding, generated SDK, service
description, header, and interceptor contracts. `@ifx/rpc-sdk-rsocket` owns
RSocket/WebSocket dependencies and supports all interaction types.
`@ifx/rpc-sdk-jsonrpc` uses Fetch and supports notifications and
request/response; request streams fail explicitly because JSON-RPC over HTTP
has no standard streaming interaction. The RSocket dependencies remain pinned
to `1.0.0-alpha.3`; this upstream API is still an alpha.

- 
- ifx.Kotlin
  - Build conventions / templates
    -  √ Experiment: JetBrains Amper
    - Gradle  
  - √ Service Hosting
    - Config mgmt
  - √ Logging
    - MDC
  - Naming conventions enforcement
  - √ Serialization
  - √ Proxy
    - Experiment: Transparent proxy
  - Protocols
    - √ RSocket
    - √ JSON-RPC over HTTP
    - GRPC
    - √ Invocation
      - √ sync 
      - √ async
      - √ streaming  
  - Message Bus
    - √ In-memory
    - √ Azure
    - GCP
    - Amazon
  - Extensibility pipeline:
    - √ Context Propagation
    - √ Request / Response logging
    - Observability
    - Telemetry
    - Encryption
    - Distributed Tracing
    - Reliable messaging (Maybe part of protocol instead?)
  - Javascript SDK (generated)
  - Distributed Transactions
  - Test Harness and tooling
    - Test client (GUI?)
  - Workflow support (Camunda etc)

- ifx.cloud


- ifx.office
  - Interviewing guidelines / template
  - System Design Report template
  - Project Design Report template 
  - Figma / Visio / LucidChart tooling


Service discovery
Test client with UI
/Transparent/ proxy






## Rationale 
In the name of efficiency, effectiveness and productivity:  
   
* Improve consistency  
* Enforce policy  
* Lower the bar of entry  
* Remove boilerplate  
* Sand down rough edges  
* Extract razorblades

###  iFX should
-   Mitigate technology as a risk
-   Wrap best practice usage (consistent+repeatable)
-   Enforce policy
-   Lower the bar of entry for Dev Community
-   Demystify consumption
-   Convention over configuration


# What is it
Infrastructure. For code
 -  Security 
-   Logging 
-   Diagnostics 
-   Setup 
-   Instrumentation 
-   Control and administration
-   Invocation
-   Etc
![[Pasted image 20221025080817.png]]
![[Pasted image 20221025080417.png]]

## Components
Hand off point
Framework for running and testing services
Communication layer - isolate business (service) code
Formalized guidelines - contstraints
Hosting
Flow
Rules
Security

![[Pasted image 20221025080516.png]]


![[Pasted image 20221025080654.png]]


## Know your team
-   Never throw it over the wall
	-   You, not them, are responsible if they get it wrong
	-   You must train them…
-   In the end (as always), it’s all about planning




# Requirements for us:

## Platforms
JVM
.NET
(some way to call out to python)

## Communication
Request/response
Fire-and forget
Streaming

### Programming Model
 - State
 - Raise event (pubsub/queue)
 - Fire and forget call
 - Request/response
 - Streaming

ProxyFactory
	Invocation
	Call
	Serve

## Hosting
Endpoints

#### Protocols
RSocket
JSON-RPC
gRPC?



- Platforms
Dapr/Kubernets
Local single executable
Net
Jvm


- Message bus
- Workflow


```
Encrypted calls
Authentication
Identity propagation
Authorization
Security audits
Transactions propagation
Transactions voting 
Calls timeout
Reliability
Tracing and logging
Profiling and instrumentation
Instance management
Durability
Error masking
Fault isolation
Channel faulting
Buffering and throttling
Data versioning tolerance
Synchronization and synchronization context 
MBV
Remotability
Interoperability 
Queuing
Service bus
Discovery
```


### Project level iFx - customizations.







# Feature list:
1. Encrypted calls  
2. Authentication  
3. Identity propagation  
4. Authorization  
5. Security audit 
6. Transactions propagation  
7. Transactions voting  
8. Calls timeout  
9. Reliability  
10. Tracing and logging  
11. Profiling and instrumentation  
12. Instance management  
13. Durability  
14. Error masking  
15. Fault isolation  
16. Channel faulting  
17. Buffering and throttling  
18. Data versioning tolerance  
19. Synchronization and synchronization context  
20. MBV  
21. Remotability  
22. Interoperability  
23. Queuing  
24. Service bus  
25. Discovery




Serialization
![[Pasted image 20221031111311.png]]

![[Pasted image 20221031114433.png]]
