export const contract = `class ProductAccess(
  private val products: ProductRepository,
) : IProductAccess {
  override suspend fun filter(criteria: Criteria) =
    products.find(criteria)
}`;

export const outputs = [
  ["01", "Host + lifecycle"],
  ["02", "Typed client proxies"],
  ["03", "Transport + context"],
  ["04", "Health, logs + traces"],
  ["05", "Gateway, OpenAPI + SDK"],
] as const;

export const planes = [
  {
    number: "01",
    title: "Contract",
    kicker: "Pure Kotlin",
    copy: "Interfaces and serializable data. No transport, server, generated code, or deployment concern leaks into the service contract.",
    tags: ["IService", "Response<T>", "Flow<T>"],
  },
  {
    number: "02",
    title: "Build",
    kicker: "Compile time",
    copy: "KSP and compiler tooling derive descriptors, proxies, server bindings, wire schemas, and public API artifacts.",
    tags: ["KSP", "Compiler plugin", "SDK generation"],
  },
  {
    number: "03",
    title: "Runtime",
    kicker: "Composable",
    copy: "Hosts, transports, gateways, interceptors, diagnostics, and telemetry meet through small stable contracts.",
    tags: ["Host", "IBinding", "IProxyFactory"],
  },
] as const;

export const capabilities = [
  ["Hosting", "Register the implementation. The host drives startup, health, graceful drain, and shutdown without exposing lifecycle as RPC."],
  ["Transport", "Run the same service over RSocket or JSON-RPC. Request, response, and stream shapes come from the Kotlin signature."],
  ["Generated clients", "Call services through typed Kotlin proxies. Derive the wire schema and TypeScript contracts from the same source."],
  ["Call policies", "Propagate context and apply logging, authentication, tracing, or other interceptors around every invocation."],
  ["Observability", "Get structured logs, W3C traces, OTLP export, health probes, a service catalog, and live inspection."],
  ["Public delivery", "Project an intentional gateway surface, then derive HTTP routes, OpenAPI 3.1, SDKs, and deployable images."],
] as const;

export const avoidedWork = [
  "No route tables",
  "No handwritten clients",
  "No duplicate DTO mapping",
  "No separate health service",
] as const;

export const gettingStarted = `interface IGreeter : IService {
  suspend fun greet(request: GreetRequest): GreetResponse
}

class Greeter : IGreeter {
  override suspend fun greet(request: GreetRequest) =
    GreetResponse("Hello, \${request.name}")
}

val host = Host.default(name = "Greeter")

host.registerService<IGreeter> { Greeter() }
    .start()

val greeter = RSocketProxyFactory
    .forHost(host)
    .create<IGreeter>()`;

export const routePages = {
  infrastructure: {
    href: "/infrastructure",
    title: "Infrastructure",
    heading: "The infrastructure around your services.",
    description: "Hosting, transport, generated clients, call policies, observability, and public delivery—supplied around the business code.",
  },
  architecture: {
    href: "/architecture",
    title: "Architecture",
    heading: "One boundary. Three planes.",
    description: "See how pure Kotlin contracts connect to compile-time generation and a composable runtime.",
  },
  start: {
    href: "/get-started",
    title: "Get started",
    heading: "One interface. One implementation.",
    description: "Register a service once, then use the generated descriptor to connect both sides.",
  },
} as const;
