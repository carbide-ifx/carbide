
# iFX.kotlin

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

## Subsystem dependency

Applications that host a subsystem can use the published `ifx.subsystem` module
as their single iFX runtime dependency:

```yaml
dependencies:
  - sonat:ifx.subsystem:0.0.7
```

It exports the host, RSocket and JSON-RPC protocols and proxy factories,
interceptor contracts, context and logging support, OpenTelemetry, the actuator,
and host tooling such as `ServiceExplorer`. The dependency does not select a
protocol, enable tooling, or expose services automatically; those choices stay
explicit in the subsystem application root.

Generated service bindings still require the subsystem/application KSP setup
described below. Those processors are build-time tools rather than runtime
dependencies.

The bundle supports JVM and macOS ARM64 and publishes platform-correct
multiplatform metadata.

## Multi-protocol hosting

`Host` owns service registration and the lifecycle of its Ktor servers. Each
configured listener exposes exactly one protocol on its own port, and every
listener receives the same registered service endpoints. Protocol implementations
only install their routes and wire handling into the listener provided by the
host.

```kotlin
import ifx.subsystem.subsystem

val host = Host.subsystem(name = "Example System") {
    val rsocket = listen(RSocketServerProtocol(), port = 7000)
    listen(JsonRpcServerProtocol(), port = 7001)
    install(ServiceExplorer(rsocket))
}

host.registerService<AwesomeService> { AwesomeServiceImpl() }
host.open()

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

The `ifx.subsystem` bundle provides a default host that exposes RSocket on the
requested port. Passing `0` selects an available port:

```kotlin
import ifx.subsystem.default

val host = Host.default(port = 8080)
val testHost = Host.default()
```

The service explorer remains opt-in and can be installed through the host
builder as shown above. For a custom listener configuration, use the generated
subsystem host convenience:

```kotlin
import ifx.subsystem.subsystem

val host = Host.subsystem {
    listen(RSocketServerProtocol())
}
```

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
fields. Every value placed in it is serialized immediately and propagated by a
zero-configuration `ContextInterceptor`:

```kotlin
@Serializable
@SerialName("ifx.caller")
data class Caller(val subject: String)

@Serializable
@SerialName("ifx.request")
data class RequestMetadata(val requestId: String)

val contextInterceptor = ContextInterceptor()

val interceptors = listOf(
    LoggingInterceptor(),
    contextInterceptor,
    Encryption,
)

host.addInterceptors(interceptors)
proxyFactory.addInterceptors(interceptors)

withContext(Context().set(Caller("user-42"))) {
    client.awesome(request)
}
```

On the server, propagated values are installed in the coroutine context
for the complete invocation, including stream collection, and can be read with
`Context.current().getOrNull<Caller>()`. Context values must be serializable;
use a stable `@SerialName` as their cross-system identity. Unknown values remain
as opaque JSON and can pass through systems that do not understand them. Place
the context interceptor before interceptors that encode or encrypt headers;
reversed server ordering will decrypt the headers before context extraction.
Generic JSON headers can be inspected or changed with `Message.headers()` and
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

Register the separate actuator service to expose that flow through the normal
service transport. Callers use the generated actuator client or an iFX proxy;
there is no separate HTTP streaming endpoint:

```kotlin
host.registerActuator()

val actuator = proxyFactory.create<IActuator>()
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
        - sonat:ifx.rpc.index.ksp:0.0.7
```

A subsystem's KSP run reads every reachable dependency index and generates the Kotlin
descriptors, proxies, one reflection-free descriptor registry, and host conveniences
that supply that registry internally. Each module represents at most one subsystem.

Only subsystem/application modules apply the RPC generators:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - sonat:ifx.rpc.index.ksp:0.0.7
        - sonat:ifx.rpc.ksp:0.0.7
        # Optional: generate TypeScript contracts and wire types.
        - sonat:ifx.rpc.typescript.ksp:0.0.7
```

The subsystem dependency graph is the contract manifest. Contract modules depend only
on `ifx.service` at runtime; they do not generate RPC bindings or depend on protocol code.
Adding or removing a service-module dependency changes the generated registry without a
second service list or annotation. A shared module template may apply the index processor
to every module; it emits nothing for modules that do not declare service contracts.

The generated `Host.subsystem` convenience supports custom listener configurations, and
the generated overload of `Host.default` delegates to the RSocket default owned by
`ifx.subsystem`. Application code does not handle the generated registry. Proxy factories
created with `forHost` reuse the host registry automatically:

```kotlin
import ifx.subsystem.subsystem

val host = Host.subsystem(name = "Test System") {
    listen(RSocketServerProtocol())
}

host.registerService<IProductAccess> { ProductAccessEmulator() }
```

In a multiplatform application, KSP emits the registry and conveniences into each
platform source set. Keep host assembly in the corresponding platform source sets.

The registry-taking `Host` constructors and `Host.default` overload remain available for
advanced composition, but normal subsystem code should use the generated conveniences.

Generation does not expose a contract. Only `registerService` publishes an endpoint.
The registry works on JVM and Kotlin/Native without runtime classpath scanning or
associated-object mutation of dependency contracts.

The optional `ifx.rpc.typescript.ksp` processor generates a TypeScript service
interface, operation request/response aliases, and all reachable serializable
types. User-defined request and response types must use `@Serializable` and
custom or contextual serializers are rejected because their wire shape cannot
be inferred from KSP symbols.

## Interactive service explorer

The optional `ifx.host.tooling` module provides a `ServiceExplorer` host
extension. It targets an RSocket listener because the explorer invokes services
through RSocket, but remains separate from both the listener configuration and
the RSocket protocol implementation. The explorer is off by default because it
can invoke mutating operations. The landing page shows the service components
registered by that host. Selecting a component opens its operations, generates
request controls from the serialized wire types, and displays request/response,
fire-and-forget, and streaming results.

For example, a host resolved to port `8080` exposes the UI at
`http://localhost:8080/` and its machine-readable service catalog at
`http://localhost:8080/ifx/services`.

```kotlin
val host = Host.subsystem(name = "Test System") {
    val rsocket = listen(RSocketServerProtocol(), port = 8080)
    install(ServiceExplorer(rsocket))
}
```

Generated TypeScript contracts export a `{Service}Description` value in
addition to their typed client. It contains the same operations and runtime
wire-type schema used by the hosted explorer, so other development tools can
reuse the metadata without attempting to reflect on erased TypeScript types.

Each generated contract also contains a protocol-neutral concrete
`{Service}Client`. Choose a separate protocol package when connecting it. The
protocol client appends the generated service address to its base URL, while the
generated service client sends the exact Kotlin operation signatures through
the selected binding:

```typescript
import { RSocketClient } from "@ifx/rpc-client-rsocket";
import { JsonRpcClient } from "@ifx/rpc-client-jsonrpc";
import { ISalesManagerClient } from "./generated/ISalesManager";

const streamingClient = await RSocketClient.connect(
    ISalesManagerClient,
    "ws://localhost:7000",
);
const httpClient = await JsonRpcClient.connect(
    ISalesManagerClient,
    "http://localhost:7001",
);

try {
  for await (const product of streamingClient.listProducts()) {
    console.log(product)
  }
} finally {
  streamingClient.close()
  httpClient.close()
}
```

`@ifx/rpc-client` contains only the shared binding, generated-client, service
description, header, and interceptor contracts. `@ifx/rpc-client-rsocket` owns
RSocket/WebSocket dependencies and supports all interaction types.
`@ifx/rpc-client-jsonrpc` uses Fetch and supports notifications and
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
