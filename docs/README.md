# Carbide architecture documentation

This directory describes what Carbide is made of and how the pieces fit together. It is design
documentation, not a user guide — the [root README](../README.md) remains the task-oriented guide
("how do I register a service", "how do I enable Jib").

Read [Getting started](getting-started.md) first if you have not used Carbide before. Then, in order:

| Document | Answers |
| --- | --- |
| [Getting started](getting-started.md) | How do I define a contract, host it, and call it — the tutorial path |
| [Architecture overview](architecture.md) | What is Carbide, what are its planes and layers, how does a call get from a caller to a service |
| [Diagrams](diagrams.md) | Ten views of how the pieces fit: context, module graph, process anatomy, object model, build pipeline, startup, streaming call, shutdown, topologies |
| [Module catalog](module-catalog.md) | One entry per module: responsibility, boundaries, dependencies |
| [Code generation pipeline](code-generation.md) | How a Kotlin interface becomes a descriptor, a proxy, a server binding, and a TypeScript SDK |
| [Call path and interceptors](call-path.md) | The invocation model, the onion, context propagation, error and lifecycle semantics |
| [Gateway design](gateway.md) | Projections, endpoint sources, embedded vs standalone gateways, OpenAPI |
| [TypeScript SDK](typescript-sdk.md) | The npm packages, how generated contracts bind to a protocol |
| [Design decisions](design-decisions.md) | Why the architecture is shaped this way, and what each decision costs |
| [Publishing](publishing.md) | Which modules form the supported Maven surface and how releases are staged |

## Conventions used throughout

- **Contract module** — a module named `*.contract` containing only interfaces and data types. It has
  no transport, no server, and no generated code.
- **Address** — the fully qualified name of a service contract interface. It is the routing key on
  every protocol, the key in the binding cache, and the identity in logs and the actuator.
- **Operation** — one function on a service contract. Its name is the route within an address.
- **Interaction type** — `fireAndForget`, `requestResponse`, or `requestStream`; derived from the
  operation signature, never declared by hand except for the `@FireAndForget` marker.
