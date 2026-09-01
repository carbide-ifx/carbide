# Design decisions

Each entry states the decision, why it was made, and what it costs. Where a decision is visible in the
code, the relevant type is named. This is the document to update when a decision is revisited.

---

## The service contract is a plain Kotlin interface

**Decision.** A service is an interface extending `IService`. No annotations, no IDL, no schema file,
no base class.

**Why.** The contract stays readable and refactorable with ordinary IDE tooling, and there is exactly
one definition of an operation. An IDL would add a second source of truth and a translation step; an
annotation-driven model would let the contract and its exposure drift.

**Cost.** Everything the framework needs must be inferable from the signature. Interaction type is
derived (a `Flow` return is a stream), which means `@FireAndForget` has to exist as the one thing that
*cannot* be inferred — a suspending `Unit` operation is ambiguous between one-way and
wait-for-completion. Carbide resolves that ambiguity toward the safer default and makes one-way opt-in.

---

## Wiring is resolved at compile time, never at runtime

**Decision.** No classpath scanning, no reflection, no service locator, no runtime registry. KSP
generates descriptors; a Kotlin IR plugin links them at the call site.

**Why.** Runtime discovery fails at runtime, in production, on the machine where the classpath differs.
Compile-time linking turns "the descriptor for this service is missing" into a build error naming the
contract. It also works on Kotlin/Native, where reflection is not an option.

**Cost.** Subsystem modules must apply both `ifx.subsystem.ksp` and the `ifx.rpc.compiler` plugin. The
reified conveniences (`registerService<T>`, `create<T>()`) are intrinsics that fail loudly without the
plugin. Native needs a two-round KSP protocol — an empty package anchor in round one — because
dependency KLIB indexes are not visible in the first round. Consumers without the plugin can still
pass descriptors explicitly.

---

## The dependency graph is the service manifest

**Decision.** A subsystem's descriptors come from the `@IfxServiceIndex` objects reachable through its
dependencies. There is no service list, no registry file, and no aggregate generated registry.

**Why.** A second list is a second thing to forget. Adding a service-module dependency and using the
service are the same action.

**Cost.** Contract modules must apply `ifx.contract.ksp`; forgetting it means a contract is invisible
downstream. The index is per-module and aggregating, so KSP dependency declarations must stay correct
for incremental builds. Applying the index processor to a non-contract module is harmless but
pointless.

---

## Generation does not expose anything

**Decision.** Generating a descriptor makes a service *callable*. Only `registerService` makes it
*reachable*.

**Why.** Otherwise a transitive dependency could silently publish an endpoint, and the reachable API
surface of a process would be a property of its dependency graph rather than of its composition root.

**Cost.** Descriptors are generated for contracts a subsystem may never host — a small amount of dead
generated code in exchange for the invariant.

---

## `IBinding` is the single transport abstraction

**Decision.** Three interaction types, `Message(header, body)`, one interface. Interceptor pipelines,
protocol clients, generated server bindings, and gateways are all `IBinding` implementations.

**Why.** A uniform pivot means an interceptor written once works on the client, on the server, under
every protocol, and in front of a gateway. It also makes an in-process or test binding a first-class
citizen rather than a mock.

**Cost.** The abstraction is JSON-string-shaped, so a binary or zero-copy transport does not fit
without changing `Message`. This is a deliberate ceiling: see *JSON everywhere* below.

---

## One invocation is one cold `Flow<Message>`

**Decision.** Fire-and-forget emits nothing, request/response emits once, request-stream emits
normally. `InterceptorPipeline` collects, takes `single()`, or passes through accordingly.

**Why.** One model means an interceptor's `try`/`finally` surrounds the *complete* lifetime of a call,
including stream collection. Cleanup, failure handling, cancellation, and telemetry spans then behave
identically across interaction types, instead of needing three code paths and getting streams wrong.

**Cost.** Interceptor authors must use a `flow { }` block and `emitAll(next(call))` rather than a
straightforward `suspend` call. Getting this wrong produces a layer that closes its resource before the
stream finishes.

One `InterceptorPipeline` serves both directions. `InterceptorCall` carries a `CallDirection` rather
than being a sealed pair of `ClientCall` / `ServerCall` types, so `copy()` replaces a per-subtype
`withMessage` and the pipeline needs no subclass per side.

---

## Interceptor order is reversed on the server

**Decision.** Client interceptors run in registration order; server interceptors run in reverse.

**Why.** A single shared list produces a symmetric onion, so `[logging, encryption]` on both sides
composes correctly without a separate server list. `Rot13Interceptor` exists in the codebase as the
worked example that makes asymmetry visible.

**Cost.** Ordering is subtle and matters. Telemetry must be registered *before* any header-encrypting
layer, so that on the reversed server side decryption happens before `traceparent` extraction. This is
the framework's most easily misconfigured detail.

---

## Three interceptors are mandatory and cannot be replaced

**Decision.** Every `Host` installs `ContextInterceptor`, `UnhandledExceptionInterceptor`, and
`HostLifecycleInterceptor` per service. `interceptors = ...` only *adds* layers.

**Why.** Context propagation, error reporting, and drain accounting are invariants the framework
guarantees. If they were replaceable, no other component could rely on them.

**Cost.** A caller cannot opt out of the small overhead. Ordering is fixed: lifecycle outermost so it
counts the whole invocation, context innermost so caller layers can decode headers first.

---

## Context has no predefined fields

**Decision.** `Context` is an opaque `Map<String, JsonElement>` keyed by `@SerialName`. Carbide does not
define a caller, tenant, or request id.

**Why.** Every organization means something different by "caller". Values serialize on `set`, so a
system that does not know a type still propagates it untouched — which is what makes context work
across systems that were not built together.

**Cost.** `@SerialName` becomes a cross-system contract that must stay stable; renaming one silently
breaks propagation. Values must be `@Serializable`, and every `set` costs a serialization.

---

## One protocol per listener, one Ktor application per listener

**Decision.** Each listener exposes exactly one protocol on its own port and interface. Protocols never
share a Ktor `Application`.

**Why.** Routes and plugins cannot collide, each listener gets an independent boundary for TLS and
authentication, and internal versus public becomes a structural difference rather than a filter.

**Cost.** More ports to configure and expose. `port = 0` plus `host.port(protocolId)` handles tests and
dynamic environments.

Extensions are declared inside the listener they extend, so the host never reconciles an extension
with a listener it does not own. An extension that only works on one protocol says so with
`requiredProtocolId`, which the listener checks at install time.

---

## Calls have no built-in timeout, and are never retried

**Decision.** Only connection acquisition is bounded client-side. An application deadline is the
caller's `withTimeout`. A failed call is never replayed.

**Why.** A framework-chosen deadline is wrong for both a 5 ms lookup and a 10 minute report, and a
transparent retry silently duplicates non-idempotent work. Neither is a decision infrastructure should
make on the application's behalf.

**Cost.** The RSocket keep-alive becomes the worst-case detector for a dead peer — the protocol default
(20 s interval, 90 s lifetime) means a call can hang roughly 110 s. Tuning `KeepAlive` per factory is
therefore a real operational decision, not an optimization. Reliability and delivery guarantees remain
open items.

---

## Ktor in the host contract {#ktor-in-the-host-contract}

**Decision.** `ifx.protocol.contract` has no transport dependency, but `ifx.host.contract` exports
`ktor-server-core` because `IServerProtocol.install` receives a Ktor `Application`.

**Why.** A fully abstract server contract would need an adapter layer reproducing Ktor's routing,
plugin, and lifecycle model, for a substitution nobody has asked for. The coupling was accepted where
it is cheapest to reverse and kept out of the layer that matters most.

**Cost.** A non-Ktor server protocol cannot be written against `ifx.host.contract` as it stands. The
*client* side has no such coupling — `IClientProtocol` and `IBinding` are transport-free — so a gateway,
a test double, and an in-process binding remain trivial.

---

## JSON everywhere

**Decision.** `Message` is two JSON strings. `RpcFormat` is a shared `kotlinx.serialization` `Json`
with `encodeDefaults = true`.

**Why.** Headers stay independently readable and rewritable without touching the payload; unknown
values pass through untouched; the same wire shape serves Kotlin, TypeScript, OpenAPI, and the
Service Explorer. Debuggability across a polyglot estate outweighs bytes on the wire.

**Cost.** No binary encoding, no zero-copy path, and headers are re-parsed by each layer that inspects
them. `encodeDefaults = true` makes payloads larger but makes the wire shape explicit and stable — the
same trade in the other direction.

---

## JSON-RPC refuses to stream

**Decision.** Calling a `Flow`-returning operation through the JSON-RPC client fails explicitly, on
both the Kotlin and TypeScript sides.

**Why.** JSON-RPC over HTTP has no standard streaming interaction. Emulating one with polling or a
proprietary framing would produce something that looks portable and is not.

**Cost.** A service with streaming operations is not fully usable over JSON-RPC. Use RSocket, or the
gateway HTTP surface with its explicit NDJSON convention.

---

## The gateway HTTP surface is separate from JSON-RPC

**Decision.** `GatewayHttpServerProtocol` publishes `POST /api/{surface}/{service}/{operation}` with
NDJSON streams, and is a different protocol from `JsonRpcServerProtocol`.

**Why.** URLs, envelopes, error shapes, fire-and-forget responses, and streaming semantics genuinely
differ. Merging them would produce a protocol that satisfies neither audience.

**Cost.** Two HTTP protocols, two TypeScript packages (`@ifx/rpc-sdk-http` and
`@ifx/rpc-sdk-jsonrpc`), and a choice a newcomer has to understand.

---

## A gateway is a projection, not a service

**Decision.** `gateway { expose(descriptor) { only(...) } }` names existing operations. In the common
case there are no DTOs, no mapping, no annotations, no duplicate interfaces.

**Why.** Re-declared interfaces and hand-written mapping are where public APIs drift from the services
behind them. Naming operations by typed `OperationDescriptor` turns a signature change into a compile
error in the projection.

**Cost.** The public surface is coupled to internal signatures — an internal rename is a public change
unless renamed explicitly with `named(...)`. When the public shape must genuinely differ from the
internal one, a real Manager operation is the answer, not the gateway.

---

## Build artifacts are rendered without a runtime

**Decision.** `gatewayArtifacts` writes `sdk.ts` and `openapi.json` per public address from a generated
projection index, loading only the declaring module's own JAR.

**Why.** API catalogs and npm publishing should not require starting a host or duplicating the DSL in
build configuration. Loading only the module's own index prevents a gateway from accidentally
publishing its dependencies' projections.

**Cost.** Projections must be non-private, non-mutable top-level `val`s — enforced with a compile error
— because the build index must reference one static value.

---

## The actuator is a service, not an HTTP endpoint

**Decision.** Catalog, health, and the streaming log tail are operations on `IActuator : IUtility`,
reached through the ordinary transport. The only HTTP endpoints are the Kubernetes probes at
`/ifx/health`, `/ifx/health/ready`, `/ifx/health/live`.

**Why.** Diagnostics get authentication, interceptors, context propagation, and streaming for free, and
the Service Explorer needs no special protocol. Kubernetes probes are the exception because kubelet
speaks HTTP and nothing else.

**Cost.** A plain `curl` cannot read the catalog; a client or the explorer is required. Utility
`health()` needs an explicit carve-out in the drain gate so probes keep answering during shutdown.

---

## Bundled frontend assets, two packaging strategies

**Decision.** `ifx.service-explorer` ships the built UI: ordinary JAR resources on JVM, a generated
compressed asset projection on Native.

**Why.** `Host.default()` should give a working explorer with no frontend configuration. Native library
resources require application-level packaging, so the assets are generated into Kotlin source.

**Cost.** The generated Native projection is a build output committed alongside the module and must be
regenerated when the frontend changes. `ifx.host.webapp` remains the general, unbundled mechanism for
serving any web build.

---

## Multiplatform targets JVM and macosArm64

**Decision.** Runtime modules publish for JVM and macOS ARM64. Build-time tooling and `utility.db` are
JVM-only.

**Why.** JVM is the deployment target; macosArm64 keeps the Native code path honest and gives fast
local Native builds on developer machines.

**Cost.** Native support constrains implementation choices throughout — no reflection, platform-split
Ktor client engines, the two-round KSP protocol, the compressed asset projection — for a target that is
currently a correctness proof rather than a deployment platform. Linux ARM/x64 Native targets are not
published.

---

## Terpal is vendored and rebuilt

**Decision.** `terpal.compiler-plugin` downloads checksum-pinned upstream sources, applies a one-line
Kotlin compiler API compatibility change, and publishes to Maven Local before a clean build.

**Why.** Amper resolves third-party compiler plugins from Maven repositories, and no upstream release
is compatible with Kotlin 2.4. Two explicit build phases are honest about that; a hidden bootstrap
would not be.

**Cost.** A manual `./terpal.compiler-plugin/publish-local` before the first build, and a patch to carry
until upstream catches up. Only `utility.db` depends on it.

---

## `ifx.jib` is vendored per repository

**Decision.** The container-image plugin lives in this repository as a local Amper plugin.

**Why.** Kotlin Toolchain currently supports only local custom plugin modules.

**Cost.** Downstream repositories must copy the module until external plugin publication is supported.
This is a workaround with a known expiry, not a design position.

---

## `ifx.subsystem` is a curated bundle, not a framework entry point

**Decision.** One dependency re-exporting the standard runtime set, adding exactly one function
(`Host.default()`).

**Why.** Applications should not assemble a dependency list to get started, but nothing in the default
composition should be privileged. Constructing `Host` directly must remain a first-class path.

**Cost.** The export list is hand-maintained and deliberately explicit — see the comment in
`ifx.subsystem/module.yaml`. `Host.default()` is suspending because registering the actuator is, which
surprises callers who expect a plain factory.

---

## Open items

Recorded here so the gaps are visible rather than implied. From the feature list in the root README,
these remain unaddressed or partial:

| Area | Status |
| --- | --- |
| Message bus / queuing / pub-sub | `utility.event` is legacy Gradle code outside the Amper build |
| gRPC protocol | Not implemented; the `IServerProtocol` / `IClientProtocol` seams exist for it |
| Service discovery | Endpoints are static configuration (`ServiceEndpoint`) resolved at the composition root |
| Distributed transactions, propagation, voting | Not implemented |
| Reliability, buffering, throttling, durability | Not implemented; no retry by design |
| Authorization, security audit, encrypted calls | Seams exist (authenticators, interceptors); no built-in implementation |
| Data versioning tolerance | Only what `kotlinx.serialization` and opaque context/header pass-through provide |
| .NET interoperability | The wire format is designed for it; no .NET binding exists |
