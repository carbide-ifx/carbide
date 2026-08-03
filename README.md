
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

There is no descriptor registry and no service annotation. The KSP processor generates
the descriptor, and the compiler plugin associates the service contract with it on
Kotlin/Native. JVM resolves the same generated descriptor by convention.

Modules declaring service contracts need both integrations:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - ../ifx.rpc.ksp
        - ../ifx.rpc.typescript.ksp
    compilerPlugins:
      - id: ifx.rpc.compiler
        dependency: sonat:ifx-rpc-compiler-plugin:0.0.6
```

The optional `ifx.rpc.typescript.ksp` processor generates a TypeScript service
interface, operation request/response aliases, and all reachable serializable
types. User-defined request and response types must use `@Serializable` and
custom or contextual serializers are rejected because their wire shape cannot
be inferred from KSP symbols.

Each generated contract also contains a concrete `{Service}Client`. Its
`connect()` factory uses the TypeScript runtime in
`typescript/ifx-rpc-client`, appends the qualified service address to the
WebSocket base URL, and sends the exact Kotlin operation signatures as RSocket
routing metadata:

```typescript
const client = await ISalesManagerClient.connect("ws://localhost:8080")

try {
    for await (const product of client.listProducts()) {
        console.log(product)
    }
} finally {
    client.close()
}
```

The runtime exposes raw message-stream interceptors for context propagation,
encryption, tracing, or other cross-cutting wire behavior. Its RSocket
dependencies are pinned to the newest published TypeScript line,
`1.0.0-alpha.3`; this upstream API is still an alpha.

Amper consumes compiler plugins as Maven artifacts, so publish the compiler-plugin
module locally before building modules that use it:

```shell
./kotlin publish local -m ifx.rpc.compiler-plugin
./kotlin build
```

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
    - JSON-RPC
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
