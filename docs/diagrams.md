# Diagrams

A visual index of how Carbide fits together, from the outside in. Each diagram is followed by what to
read from it. Diagrams are Mermaid so they render on GitHub and stay diffable in review.

1. [System context](#1-system-context) — what talks to a Carbide system
2. [Module dependency graph](#2-module-dependency-graph) — the real graph, verified against `module.yaml`
3. [The contract / implementation pattern](#3-the-contract--implementation-pattern)
4. [Anatomy of a subsystem process](#4-anatomy-of-a-subsystem-process)
5. [Runtime object model](#5-runtime-object-model)
6. [Build-time pipeline](#6-build-time-pipeline)
7. [Startup sequence](#7-startup-sequence)
8. [A streaming call, end to end](#8-a-streaming-call-end-to-end)
9. [Shutdown and draining](#9-shutdown-and-draining)
10. [Deployment topologies](#10-deployment-topologies)

---

## 1. System context

```mermaid
flowchart TB
    browser["Browser / SPA<br/><i>generated TS SDK</i>"]
    partner["Partner or public client<br/><i>OpenAPI / HTTP</i>"]
    dev["Developer<br/><i>Service Explorer</i>"]
    k8s["Kubernetes kubelet"]
    otel["OTLP collector"]

    subgraph estate["Carbide estate"]
        gw["Gateway surface<br/><i>projection of selected operations</i>"]
        s1["Subsystem A<br/><i>Managers</i>"]
        s2["Subsystem B<br/><i>Engines, ResourceAccess</i>"]
    end

    db[("PostgreSQL<br/><i>utility.db</i>")]

    browser -->|"RSocket / WS · JSON-RPC"| gw
    partner -->|"POST /api/{surface}/…"| gw
    dev -->|"HTTP :8080/"| s1
    k8s -->|"/ifx/health/ready"| s1
    gw --> s1
    s1 -->|"RSocket, typed proxy"| s2
    s2 --> db
    s1 -.->|"spans"| otel
    s2 -.->|"spans"| otel
```

**Read:** every arrow into the estate is one of four surfaces — a gateway projection, the ordinary
service transport, the Kubernetes probes, or the developer explorer. There is no fifth. Everything
public goes through a projection; everything internal is a typed proxy over the service transport.

---

## 2. Module dependency graph

The **transitive reduction** of the real `ifx.*` production graph: 30 of the 67 declared
`ifx.* → ifx.*` dependencies. The other 37 are implied transitively — a module naming a grandparent
explicitly, which Amper modules do routinely — and are omitted for legibility. Generated from the
`module.yaml` files, not from memory.

```mermaid
flowchart BT
    subgraph base["foundation"]
        ifx_context["ifx.context"]
        ifx_logging["ifx.logging"]
        ifx_service["ifx.service"]
    end

    subgraph contracts["contracts"]
        ifx_protocol_contract["ifx.protocol.contract"]
        ifx_host_contract["ifx.host.contract"]
        ifx_gateway_contract["ifx.gateway.contract"]
    end

    subgraph impl["implementations"]
        ifx_host["ifx.host"]
        ifx_proxy_factory["ifx.proxy-factory"]
        ifx_gateway["ifx.gateway"]
        ifx_protocol_rsocket["ifx.protocol.rsocket"]
        ifx_protocol_jsonrpc["ifx.protocol.jsonrpc"]
        ifx_proxy_factory_rsocket["ifx.proxy-factory.rsocket"]
        ifx_proxy_factory_jsonrpc["ifx.proxy-factory.jsonrpc"]
        ifx_gateway_ktor["ifx.gateway.ktor"]
        ifx_gateway_typescript["ifx.gateway.typescript"]
    end

    subgraph tooling["tooling and extensions"]
        ifx_actuator["ifx.actuator"]
        ifx_host_webapp["ifx.host.webapp"]
        ifx_service_explorer["ifx.service-explorer"]
        ifx_telemetry_otel["ifx.telemetry.otel"]
        ifx_telemetry_otel_ktor_client["ifx.telemetry.otel.ktor-client"]
        ifx_testing["ifx.testing"]
    end

    ifx_subsystem["<b>ifx.subsystem</b><br/><i>the single app dependency</i>"]

    ifx_service --> ifx_context
    ifx_service --> ifx_logging
    ifx_protocol_contract --> ifx_service
    ifx_host_contract --> ifx_protocol_contract
    ifx_proxy_factory --> ifx_protocol_contract
    ifx_gateway_contract --> ifx_protocol_contract
    ifx_host --> ifx_host_contract
    ifx_gateway --> ifx_gateway_contract
    ifx_gateway --> ifx_host_contract
    ifx_protocol_rsocket --> ifx_host_contract
    ifx_protocol_jsonrpc --> ifx_host_contract
    ifx_proxy_factory_rsocket --> ifx_protocol_rsocket
    ifx_proxy_factory_rsocket --> ifx_proxy_factory
    ifx_proxy_factory_jsonrpc --> ifx_protocol_jsonrpc
    ifx_proxy_factory_jsonrpc --> ifx_proxy_factory
    ifx_gateway_ktor --> ifx_gateway
    ifx_gateway_typescript --> ifx_gateway_contract
    ifx_actuator --> ifx_host_contract
    ifx_host_webapp --> ifx_host_contract
    ifx_service_explorer --> ifx_host
    ifx_service_explorer --> ifx_host_webapp
    ifx_service_explorer --> ifx_protocol_rsocket
    ifx_telemetry_otel --> ifx_protocol_contract
    ifx_telemetry_otel_ktor_client --> ifx_telemetry_otel
    ifx_testing --> ifx_service
    ifx_subsystem --> ifx_actuator
    ifx_subsystem --> ifx_service_explorer
    ifx_subsystem --> ifx_proxy_factory_rsocket
    ifx_subsystem --> ifx_proxy_factory_jsonrpc
    ifx_subsystem --> ifx_telemetry_otel
```

**Read:**

- Arrows point *downward* to dependencies. The graph is acyclic and narrow at the bottom:
  `ifx.context` and `ifx.logging` depend on nothing in Carbide.
- `ifx.protocol.contract` is the waist. Everything above it goes through it; it depends only on
  `ifx.service`. This is why `IBinding` can be implemented by a gateway, a test double, or a real
  transport without any of them knowing about each other.
- The three parallel stacks — protocol, proxy-factory, gateway — each have contract → base →
  per-technology modules, and never depend on each other's implementations.
- `ifx.subsystem` is a leaf that only aggregates. Removing it would cost applications a longer
  dependency list and nothing else.
- `ifx.gateway.artifacts` and `ifx.jib` are omitted: they are Amper build plugins, not runtime nodes.

---

## 3. The contract / implementation pattern

The same shape repeats four times. Learn it once.

```mermaid
flowchart LR
    subgraph pat["the pattern"]
        c["*.contract<br/><i>interfaces only</i>"] --> b["base<br/><i>technology-free logic</i>"] --> t1["technology A"]
        b --> t2["technology B"]
    end
```

| Concern | Contract | Base | Technologies |
| --- | --- | --- | --- |
| Serving | `ifx.host.contract`<br/>`IHost`, `IServerProtocol` | `ifx.host`<br/>`Host` | `ifx.protocol.rsocket`, `ifx.protocol.jsonrpc`, `ifx.gateway.ktor` |
| Calling | `ifx.proxy-factory`<br/>`IProxyFactory` + binding cache | — | `ifx.proxy-factory.rsocket`, `ifx.proxy-factory.jsonrpc` |
| Wire | `ifx.protocol.contract`<br/>`IBinding`, `Message` | — | any `IBinding` implementation |
| Public API | `ifx.gateway.contract`<br/>`GatewayProjection` | `ifx.gateway`<br/>`GatewayBinding` | `ifx.gateway.ktor`, `ifx.gateway.typescript` |

**Read:** a technology module is always a leaf. Choosing RSocket over JSON-RPC changes which leaf the
composition root imports — never a contract, never the base, never a service.

---

## 4. Anatomy of a subsystem process

What `Host.default(rsocketPort = 8080, jsonRpcPort = 8081)` actually produces.

```mermaid
flowchart TB
    subgraph proc["Subsystem process"]
        subgraph l1["listener 'rsocket' — Ktor app on :8080"]
            r1["RSocket routes<br/><i>one per service address</i>"]
            r2["ServiceExplorer<br/><i>GET / , /test-ui.js</i>"]
            r3["HealthEndpoints<br/><i>/ifx/health{,/ready,/live}</i>"]
        end
        subgraph l2["listener 'json-rpc' — Ktor app on :8081"]
            j1["POST /{service address}"]
        end
        subgraph reg["registered endpoints"]
            e1["ISalesManager"]
            e2["IProductAccess"]
            e3["IActuator <i>(utility)</i>"]
        end
        pf["IProxyFactory<br/><i>outbound, binding cache</i>"]
    end

    r1 --> reg
    j1 --> reg
    r2 -.->|"IActuator.catalog() over RSocket"| e3
    pf ==>|"to other subsystems"| out(["remote services"])
```

**Read:**

- Two listeners, two independent Ktor applications, two ports. They cannot collide on routes or
  plugins, and each is a separate place to terminate TLS or authenticate.
- Both listeners serve the **same** registered endpoint set by default. A gateway changes that by
  supplying an `EndpointSource` to one listener only.
- Host extensions (`ServiceExplorer`, `HealthEndpoints`) are declared inside the listener they extend
  and add HTTP routes to it. They are not services and have no address.
- The explorer has no privileged channel: it reads the catalog through `IActuator` over ordinary
  RSocket, exactly as any other client would.
- The proxy factory is outbound-only and independent of the listeners.

---

## 5. Runtime object model

```mermaid
classDiagram
    class IBinding {
        <<interface>>
        +fireAndForget(operation, message)
        +requestResponse(operation, message) Message
        +requestStream(operation, message) Flow~Message~
    }

    class Host {
        +registerService(descriptor, factory)
        +start() / stop()
        +health() HostHealth
        +serviceCatalog() ServiceCatalog
    }
    class ProtocolListener {
        +protocol: IServerProtocol
        +port, host, id
        +endpointSource: EndpointSource
    }
    class Endpoint {
        +address: String
        +binding: IBinding
        +description: ServiceDescription
    }
    class InterceptorPipeline
    class ServiceDescriptor {
        <<generated>>
        +address, description
        +createClient(binding) T
        +bind(instance) IBinding
    }
    class ProxyFactoryBase {
        +create(descriptor) T
        +at(endpoint) IProxyFactory
        -bindings: Map~destination+address, IBinding~
    }
    class HostExtension {
        <<interface>>
        +listener
        +install(application, context)
    }
    class GatewayBinding

    IBinding <|.. InterceptorPipeline
    IBinding <|.. GatewayBinding
    Host "1" *-- "1..*" ProtocolListener
    Host "1" *-- "0..*" Endpoint
    Host "1" *-- "0..*" HostExtension
    Endpoint *-- InterceptorPipeline
    InterceptorPipeline --> ServiceDescriptor : binds or creates client
```

**Read:** four different things implement `IBinding`, and each is a decoration of the next. An
`Endpoint` is not "a service" — it is an address, a binding, and a description. That is why a gateway,
which has no service instance behind it, is still a perfectly ordinary endpoint.

---

## 6. Build-time pipeline

```mermaid
flowchart TB
    subgraph m1["Contract module — applies ifx.contract.ksp"]
        a1["IProductAccess.kt<br/><i>hand-written</i>"]
        a2["@IfxServiceIndex object<br/><i>package ifx.service.index</i>"]
        a1 --> a2
    end

    subgraph m2["Subsystem module — applies ifx.subsystem.ksp + ifx.rpc.compiler"]
        b0["TestSystem.kt<br/><i>hand-written</i>"]
        b1["IProductAccessDescriptor<br/>+ proxy + server binding"]
        b2["GatewayProjectionProvider<br/><i>package ifx.gateway.index</i>"]
        b3["generated/IProductAccess.ts"]
        b4["IR rewrite:<br/>registerService&lt;T&gt; → registerService(TDescriptor)"]
        b0 --> b4
        b1 -.-> b4
    end

    subgraph out["Build outputs"]
        o1["JAR / KLIB"]
        o2["gateway/{surface}/openapi.json + sdk.ts"]
        o3["container image"]
    end

    a2 -->|"read from dependency artifacts"| b1
    a1 --> b3
    b4 --> o1
    b2 -->|"ifx.gateway.artifacts · gatewayArtifacts"| o2
    o1 -->|"ifx.jib · jibTar / jibDocker / jibPush"| o3
```

**Read:** the only hand-written files are the two white boxes. The arrow crossing the module boundary
is the whole aggregation mechanism — a subsystem learns about a contract because it *depends on* the
module that declares it, not because anyone listed it. See
[Code generation pipeline](code-generation.md).

---

## 7. Startup sequence

```mermaid
sequenceDiagram
    autonumber
    participant Main as main()
    participant Sub as ifx.subsystem
    participant H as Host
    participant L as Listeners (Ktor)
    participant S as IServiceLifecycle

    Main->>Sub: Host.default(name, rsocketPort, jsonRpcPort)
    Sub->>H: Host builder — listen(RSocket) { install(ServiceExplorer, HealthEndpoints) }, listen(JsonRpc)
    Sub->>H: registerActuator(descriptor)
    Note over H: state = NEW
    Main->>H: registerService<ISalesManager> { impl }
    Note over H: descriptor.bind(instance) wrapped in<br/>InterceptorPipeline SERVER → Endpoint
    Main->>H: start()
    Note over H: state = STARTING
    H->>S: start() for each lifecycle, in registration order
    H->>L: bind each listener (port 0 resolves now)
    L-->>H: ProtocolListenerDescription(host, port)
    H->>H: callTracker.startAccepting()
    Note over H: state = READY
    Main->>H: RSocketProxyFactory.forHost(host)
    Note over H: reads host.port("rsocket") and copies host.interceptors
```

**Read:** registration is only legal in `NEW`, and interceptors must be added before the first
registration — because the interceptor pipeline is baked into each `Endpoint` at registration time,
not consulted per call. Ports resolve at step 8, which is why `forHost` must come after `start()`.

---

## 8. A streaming call, end to end

The request/response path is in [Call path and interceptors](call-path.md). This is the streaming
case, where the "one invocation is one cold `Flow<Message>`" decision earns its keep.

```mermaid
sequenceDiagram
    autonumber
    participant App as Caller
    participant P as Generated proxy
    participant CI as Client interceptors
    participant T as RSocket transport
    participant LC as HostLifecycleInterceptor
    participant EX as UnhandledExceptionInterceptor
    participant CX as ContextInterceptor
    participant Svc as Service impl

    App->>P: for (p in stream(criteria))
    P->>CI: requestStream("stream", Message)
    Note over CI: flow { } not yet collected — cold
    CI->>T: REQUEST_STREAM frame
    T->>LC: requestStream
    LC->>LC: tracker.startCall() ✓
    LC->>EX: next(call)
    EX->>CX: next(call)
    CX->>Svc: context installed via flowOn
    loop each element
        Svc-->>CX: Product
        CX-->>T: payload frame
        T-->>App: Product
    end
    alt service throws
        Svc-->>EX: exception
        EX->>EX: log with service/class/operation
        EX-->>T: rethrow original → error frame
    end
    Note over LC: finally { tracker.finishCall() }<br/>runs after the LAST element, not the first
```

**Read:** the lifecycle layer's `finally` fires when the stream completes, so a long-running stream
keeps the host out of `STOPPING` until it finishes or `requestDrainTimeout` expires. If interceptors
wrapped only the subscription, draining would cut live streams. The error frame is per-stream — the
shared connection survives.

---

## 9. Shutdown and draining

```mermaid
sequenceDiagram
    autonumber
    participant K as Kubernetes
    participant H as Host
    participant CT as HostCallTracker
    participant S as Services
    participant L as Listeners

    K->>H: SIGTERM → stop()
    Note over H: state = DRAINING
    H->>CT: beginDrain() — refuse new calls
    Note over CT: utility health() still allowed
    par endpoint propagation
        K-->>K: /ifx/health/ready now fails,<br/>endpoints withdrawn
        Note over H: wait drainDelay
    and in-flight work
        CT->>CT: awaitIdle(requestDrainTimeout)
    end
    Note over H: state = STOPPING
    H->>S: stop() each lifecycle
    H->>L: stop each Ktor server
    H->>H: onStop actions, reverse order,<br/>all run even if one throws
    Note over H: state = STOPPED
```

**Read:** two independent clocks. `drainDelay` covers the *external* race — a load balancer that has
not noticed you yet — and defaults to zero, so it must be set to the deployment's real propagation
window. `requestDrainTimeout` (20 s) covers the *internal* one: calls already accepted. Probes keep
answering throughout, which is why utility `health()` is carved out of the drain gate.

---

## 10. Deployment topologies

The projection is identical in both. Only the `EndpointSource` configuration differs.

```mermaid
flowchart TB
    subgraph A["Embedded gateway — one process"]
        direction TB
        ca["public client"]
        subgraph pa["Subsystem process"]
            la1["public listener<br/><i>endpointSource = projection</i>"]
            la2["internal listener<br/><i>registered endpoints</i>"]
            ga["GatewayBinding"]
            sa["services"]
        end
        ia["internal peer"]
        ca --> la1 --> ga --> sa
        ia --> la2 --> sa
    end

    subgraph B["Standalone gateway — separate process"]
        direction TB
        cb["public client"]
        subgraph pb["Gateway process"]
            lb["public listener<br/><i>endpointSource = projection + remote targets</i>"]
            gb["GatewayBinding"]
        end
        subgraph pb2["Subsystem process"]
            lb2["internal listener"]
            sb["services"]
        end
        cb --> lb --> gb -->|"RSocket client binding"| lb2 --> sb
    end
```

**Read:** in both shapes the public port physically cannot reach an unprojected operation — its
listener never installed that endpoint. That is a structural boundary, not a filter someone can forget
to apply. Moving from embedded to standalone means adding `remote(descriptor, protocol)` lines; the
`gateway { }` block does not change. See [Gateway design](gateway.md).
