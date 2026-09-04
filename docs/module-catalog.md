# Module catalog

One entry per module in `project.yaml`. Each entry states what the module owns, what it deliberately
does not own, and what it depends on.

Unless noted, a module is a Kotlin Multiplatform library targeting **JVM and macosArm64**, published
to the `io.carbide-ifx` group at the version in `publishing.module-template.yaml`. Modules named `*.contract`
contain interfaces and serializable data only.

---

## Contract plane

### `ifx.context`
Ambient, immutable, serializable per-call state.

Owns `Context`, a `CoroutineContext.Element` holding a `Map<String, JsonElement>` keyed by
`@SerialName`. Values are serialized on `set`, so a system that does not know a value's type carries
it through as opaque JSON. `Context.HEADER_KEY` (`"ifx.context"`) is the reserved message header.

Does not own propagation — that is `ContextInterceptor` in `ifx.protocol.contract`. Has **no
dependencies**; it is the bottom of the graph together with `ifx.logging`.

### `ifx.logging`
Structured logging vocabulary over Kermit.

Owns `Log`, `LogTag` (the emitted structured record identity), `ServiceLogScope` (the ambient identity
of the service currently executing), and the generic writer installation point `installLogWriter`.
The host supplies the authoritative registered service identity; the inherited service logger and
`withTag` add logger-specific paths while keeping a readable console tag such as
`AwesomeServiceImpl.Repository`. Installing a writer returns an idempotent removal handle, and an
additional writer failure is reported through the standard writer without escaping into application code.

Does not own retention or the log tail — those belong to `ifx.actuator`.

### `ifx.service`
The service programming model. This is the only Carbide module a pure contract module depends on; it
exports the common context, logging, and standard-library facilities.

Owns:
- `IService` — the common service surface, including a stable logger per implementation class, and
  `IUtility` for infrastructure services.
- `IServiceLifecycle` — `start`, `stop`, `health`, driven **locally** by the host and never generated
  as remote operations.
- `ServiceHealth` — `ready` / `live` / `detail`.
- `@FireAndForget` — marks a suspending `Unit` operation as one-way. Without it, a `Unit` operation is
  request/response and waits for completion.
- `Response<T>` / `ErrorCode` — an optional `Success | Failure(errors)` result type with a stable
  cross-language JSON shape.
- `@IfxServiceIndex` — the annotation the build-time contract index is emitted as.

### `ifx.protocol.contract`
The transport-neutral RPC contract. **Depends on no transport library.**

Owns:
- `IBinding` — three interaction types; the pivot abstraction of the whole framework.
- `Message(header, body)` — two JSON strings, plus `headers()`, `withHeader()`, `context()`.
- `ServiceDescriptor<T>` — the compile-time description of a contract: `contract`, `address`,
  `description`, `createClient(binding)`, `bind(instance)`. Generated, never hand-written.
- `ServiceDescription` / `OperationDescription` / `TypeReference` / `TypeDescription` — the
  serializable runtime wire schema. This is what the Service Explorer, OpenAPI renderer, and
  TypeScript renderer all read.
- `OperationDescriptor<Service, Request, Response>` — an owner-typed reference to one operation, so
  gateway projections reject operations from the wrong service at compile time.
- `IInterceptor`, `InterceptorCall` (carrying a `CallDirection`), `InterceptorChain`, and the single
  `InterceptorPipeline` that serves both sides of the transport.
- Built-in interceptors: `ContextInterceptor` (mandatory), `UnhandledExceptionInterceptor`
  (mandatory), and `Rot13Interceptor` (a worked example of a symmetric layer). Correlated RPC
  logging is an option on `OpenTelemetryRpcInterceptor`.
- `IClientProtocol`, `Endpoint`, `ServiceEndpoint`, `ProtocolException`, `RpcFormat`.

### `ifx.host.contract`
The hosting contract. Exports `ktor-server-core` because `IServerProtocol.install` receives a Ktor
`Application`.

Owns `IHost`, `IServerProtocol`, `ProtocolListener`, `EndpointSource`,
`HostExtension` / `HostExtensionContext`, `HostState`, `HostHealth`, `ServiceHealthSnapshot`.

`EndpointSource` is the seam that lets a listener serve something other than the registered service
set — the mechanism behind embedded gateways.

### `ifx.gateway.contract`
Owns `GatewayProjection`, the `gateway { }` DSL (`expose`, `only`, `named`), `GatewayFailure`, and
`GatewayProjectionProvider` — the interface the build-time projection index implements.

---

## Runtime plane

### `ifx.host`
`Host` — the reference `IHost`. Uses Ktor CIO.

Owns listener construction and binding (port `0` resolves at startup), the service registry, the
state machine, health aggregation with a timeout, graceful drain (`drainDelay` then
`requestDrainTimeout` via `HostCallTracker`), `onStop` cleanup ordering, mandatory interceptor
installation, and `serviceCatalog()`.

Validates at construction: at least one listener, unique listener ids, and no duplicate explicit
ports. Extensions belong to their listener, so there is nothing to reconcile.

### `ifx.proxy-factory`
Owns `IProxyFactory` — `create(descriptor)`, `at(endpoint)`, `addInterceptors`, `close` — plus the
reified `create<T>()` intrinsic that fails with an explicit message when the compiler plugin is
absent, and the one implementation behind it.

`ProxyFactoryBase` is the protocol-independent factory. One atomic state is shared by every
`at(endpoint)` view. The first proxy creation freezes copied interceptor configuration; the same
state also owns the binding cache and terminal, idempotent close lifecycle. The cache is keyed by
`(ServiceEndpoint?, address)`, so every destination and service pair reuses one binding.

### `ifx.protocol.rsocket`
RSocket over WebSocket, server and client. Supports all three interaction types.

Owns `RSocketServerProtocol` (id `"rsocket"`), `RSocketClientProtocol`, `RSocketSetupAuthenticator`
(authenticates one SETUP payload and returns context trusted for the whole connection, overwriting
anything the client supplied), metadata encoding, keep-alive defaults, and Kermit logger bridging.

One RSocket route per service address. `SUBSYSTEM_KEEP_ALIVE` is the backend-to-backend default;
each connection carries its own window, so browsers keep a more generous one.
`RSocketProxyFactory.forHost(host)` reads the resolved RSocket port and copies the host's interceptors
onto the client.

### `ifx.protocol.jsonrpc`
JSON-RPC 2.0 over HTTP, server and client. Id `"json-rpc"`. Supports notifications
(fire-and-forget) and request/response. A `Flow`-returning operation **fails explicitly** — JSON-RPC
has no standard streaming interaction.
`JsonRpcProxyFactory.forHost(host)` reads the resolved JSON-RPC port and copies the host's interceptors
onto the client.

### `ifx.gateway`
`GatewayProjection.bind(resolve)` turns a projection into a single `Endpoint` whose `IBinding` routes
`"{service}/{operation}"` to a target binding. `endpointSource()` publishes that endpoint as an
`EndpointSource`, resolving each service to a locally registered binding or to an explicitly
configured remote target. Also merges the projected services' type descriptions into one schema.

### `ifx.gateway.ktor`
`GatewayHttpServerProtocol` (id `"gateway-http"`) — the conventional HTTP adapter, deliberately
separate from JSON-RPC. Publishes `POST /api/{surface}/{manager}/{operation}`, NDJSON event streams
(`next` / `complete` / `error`), a `GatewayAuthenticator` seam, and `GET /api/{surface}/openapi.json`.
`OpenApi.kt` renders OpenAPI 3.1 from the runtime wire schema.

### `ifx.gateway.typescript`
`renderTypeScriptSdk()` — a protocol-neutral TypeScript SDK for one projection, with manager
namespaces, only the projected operations, and the generated DTO shapes preserved.

### `ifx.actuator`
The diagnostics utility service.

Owns `IActuator : IUtility` (`catalog()`, `health()`, `logTail(serviceInterface): Flow<LogTailEntry>`),
`registerActuator()`, `LogTailStore` (a thread-safe per-service ring buffer, 500 entries by default),
`LogTailWriter` (the Kermit writer that retains structured entries and drops plain framework tags),
and `HealthEndpoints` — the host extension publishing `/ifx/health`, `/ifx/health/ready`,
`/ifx/health/live`.

The installed `LogTail` store is process-wide: multiple actuators in one process deliberately expose
the same retained service logs. Registration installs its writer once for the lifetime of the process.

The actuator is reached through the ordinary service transport. There is no separate HTTP catalog or
log-streaming endpoint.

### `ifx.host.webapp`
`WebApp` — a general host extension mounting a built web application directory on a listener. It
knows nothing about RPC; it serves files an npm/Vite/esbuild build produced.

### `ifx.service-explorer`
`ServiceExplorer` — the host extension serving the bundled browser UI. Requires an **RSocket**
listener, because the browser client invokes services and streams logs over RSocket. Reads the
catalog from `IActuator.catalog()` and generates request controls from the runtime wire schema.

`BundledServiceExplorerAssets` carries the npm build: ordinary JAR resources on JVM, a generated
compressed asset projection on Native (Native library resources require application-level packaging).
An optional `developmentDirectory` delegates to `WebApp` instead.

### `ifx.telemetry.otel`
Tracing without a platform-specific OpenTelemetry SDK. `Tracer` is the shared primitive for manual,
flow, RPC, and library instrumentation. It supports bounded span links for asynchronous, fan-out, and
fan-in causality; `TelemetryRuntime` owns shared configuration and lifecycle.
`OpenTelemetryRpcInterceptor` creates spans around invocations and propagates W3C `traceparent` /
`tracestate`; `OtlpHttpSpanExporter` exports
OTLP/HTTP JSON through Ktor on both JVM and macOS. `BatchSpanProcessor` provides bounded asynchronous
batching, export timeout and drop diagnostics, plus explicit flush and suspending shutdown. Export
failure never fails the RPC. `TelemetryResource` supplies immutable service, deployment, and
application-defined resource identity shared by traces and metrics. `RpcMetrics` records the standard
client and server duration histograms and `OtlpHttpMetricExporter` exports cumulative OTLP metrics
outside the RPC path.

Optional Ktor client instrumentation is included in the module. `OpenTelemetryClientPlugin` creates a client
span around each selected logical request, injects W3C trace context, and records low-cardinality
method, server, port, scheme, status, and error attributes. It is installed explicitly, so OTLP
export clients can remain uninstrumented and avoid recursive telemetry. Spans end after response
headers are received rather than asynchronously following response-body consumption.

### `ifx.subsystem`
The single runtime dependency for an application. It adds one development convenience —
`Host.development()` — and re-exports the standard runtime set. The convenience host is unauthenticated
and includes the actuator and Service Explorer; production applications compose `Host` explicitly.
The factory only assembles the host; suspending lifecycle work begins when `start()` is called.

### `ifx.testing`
Shared test scaffolding: TestBalloon framework plus Kotest assertions, and Carbide-specific assertions.

---

## Build plane

These are **`compile-only` / build-time** artifacts. They are not runtime dependencies of what they
produce. See [Code generation pipeline](code-generation.md).

| Module | Product | Role |
| --- | --- | --- |
| `ifx.contract.ksp` | `jvm/lib` KSP processor | Emits one `@IfxServiceIndex` object per contract module |
| `ifx.rpc.schema.ksp` | `jvm/lib` compiler library | Builds the one canonical service and wire-type model consumed by both KSP generators |
| `ifx.subsystem.ksp` | `jvm/lib` KSP processor | Emits a `ServiceDescriptor` + proxy + server binding per reachable contract, and the gateway projection index |
| `ifx.rpc.compiler-plugin` | `jvm/lib`, Kotlin IR plugin | Rewrites reified `registerService<T>` / `create<T>` to pass the generated descriptor; fills defaulted descriptor parameters |
| `ifx.rpc.typescript.ksp` | `jvm/lib` KSP processor | Emits TypeScript service interfaces, request/response aliases, and all reachable serializable types |
| `ifx.gateway.artifacts` | `jvm/amper-plugin` | `gatewayArtifacts` task: writes `sdk.ts` + `openapi.json` per public gateway address, without starting a host |
| `ifx.jib` | `jvm/amper-plugin` | `jibTar` / `jibDocker` / `jibPush`; non-root Java 21 distroless base, configurable extra directories |

---

## Utilities

### `utility.stdlib`
Small cross-project helpers with no Carbide dependency: `IdGenerator`, `TimeSpan`, `DbConfig`,
`CriteriaExtensions`. Multiplatform.

---

## Reference and test systems

These modules are the executable specification of the framework and its module roles.

| Module | Role |
| --- | --- |
| `test.service-contracts` | Contract-only module: `IProductAccess` (ResourceAccess), `IPricingEngine` (Engine), `ISalesManager` (Manager). Applies `ifx.contract.ksp` only |
| `test.service-aggregation` | Proves multi-module descriptor aggregation, including per-platform source sets on JVM and Native |
| `test.gateway` | Gateway projections plus RSocket and HTTP surface tests; enables the `ifx.gateway.artifacts` plugin |
| `test.test-system` | Runnable `jvm/app` subsystem with implementations, RPC interaction tests, actuator and logging tests; enables `ifx.jib` |

---

## TypeScript packages

An npm workspace under `typescript/`. See [TypeScript SDK](typescript-sdk.md).

| Package | Role |
| --- | --- |
| `@carbide-ifx/rpc-sdk` | Protocol-neutral runtime: `IfxBinding`, generated-SDK contract, service descriptions, JSON serialization, headers, outbound interceptors. No network code |
| `@carbide-ifx/rpc-sdk-rsocket` | RSocket over WebSocket. All three interaction types |
| `@carbide-ifx/rpc-sdk-jsonrpc` | JSON-RPC 2.0 over Fetch. Notifications and request/response; streams fail explicitly |
| `@carbide-ifx/rpc-sdk-http` | Conventional HTTP binding for a **gateway projection**. Separate from JSON-RPC because URLs, envelopes, errors, and streaming differ |
| `ifx-test-ui` | The Service Explorer frontend, bundled into `ifx.service-explorer` |
