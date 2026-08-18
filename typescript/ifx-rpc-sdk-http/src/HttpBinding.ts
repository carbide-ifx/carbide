import {
  IfxBindingBase,
  GatewayError,
  type GatewayFailure,
  type IfxBindingOptions,
  type IfxOutboundCall,
  type IfxServiceConstructor,
  type IfxMessage,
} from "@ifx/rpc-sdk";

export type { GatewayFailure } from "@ifx/rpc-sdk";

export type HttpRequestHeaders = HeadersInit | (() => HeadersInit | Promise<HeadersInit>);

export interface HttpBindingOptions extends IfxBindingOptions {
  readonly url: string;
  readonly fetch?: typeof globalThis.fetch;
  readonly requestHeaders?: HttpRequestHeaders;
}

export class GatewayHttpError extends GatewayError {
  constructor(
    readonly status: number,
    readonly failure: GatewayFailure,
  ) {
    super(failure);
    this.name = "GatewayHttpError";
  }
}

export class HttpBinding extends IfxBindingBase {
  static serviceUrl(baseUrl: string, serviceAddress: string): string {
    if (baseUrl.length === 0) throw new Error("The iFX HTTP base URL cannot be empty");
    if (serviceAddress.length === 0) throw new Error("The iFX gateway address cannot be empty");
    const encodedAddress = serviceAddress.split("/").map(encodeURIComponent).join("/");
    return `${baseUrl.replace(/\/+$/, "")}/api/${encodedAddress}`;
  }

  private readonly fetch: typeof globalThis.fetch;

  constructor(private readonly options: HttpBindingOptions) {
    super(options);
    this.fetch = options.fetch ?? globalThis.fetch;
    if (!this.fetch) throw new Error("Gateway HTTP requires a Fetch API implementation");
  }

  close(): void {}

  protected exchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    switch (call.interaction) {
      case "fireAndForget":
        return this.fireAndForgetExchange(call);
      case "requestResponse":
        return this.requestResponseExchange(call);
      case "requestStream":
        return this.requestStreamExchange(call);
    }
  }

  private async *fireAndForgetExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const response = await this.send(call);
    if (response.status !== 202) await throwFailure(response, call.operation);
  }

  private async *requestResponseExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const response = await this.send(call);
    if (!response.ok) await throwFailure(response, call.operation);
    yield { header: "{}", body: await response.text() };
  }

  private async *requestStreamExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const response = await this.send(call);
    if (!response.ok) await throwFailure(response, call.operation);
    if (!response.body) throw new Error(`Stream from ${call.operation} contains no response body`);

    let terminal = false;
    for await (const line of lines(response.body)) {
      if (line.trim().length === 0) continue;
      const event = parseEvent(line, call.operation);
      switch (event.type) {
        case "next":
          if (!("data" in event)) throw new Error(`Next event from ${call.operation} contains no data`);
          yield { header: "{}", body: JSON.stringify(event.data) };
          break;
        case "complete":
          terminal = true;
          return;
        case "error":
          terminal = true;
          throw new GatewayHttpError(response.status, parseFailure(event.error, call.operation));
        default:
          throw new Error(`Stream from ${call.operation} contains an unknown event type`);
      }
    }
    if (!terminal) throw new Error(`Stream from ${call.operation} ended without a terminal event`);
  }

  private async send(call: IfxOutboundCall): Promise<Response> {
    const configured = this.options.requestHeaders;
    const requestHeaders = typeof configured === "function" ? await configured() : configured;
    const headers = new Headers(requestHeaders);
    if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    const encodedOperation = call.operation.split("/").map(encodeURIComponent).join("/");
    return this.fetch(`${this.options.url.replace(/\/+$/, "")}/${encodedOperation}`, {
      method: "POST",
      headers,
      body: call.message.body.length === 0 ? undefined : call.message.body,
    });
  }
}

export class HttpSdk {
  static async connect<Sdk>(
    sdkConstructor: IfxServiceConstructor<Sdk>,
    baseUrl: string,
    options: Omit<HttpBindingOptions, "url"> = {},
  ): Promise<Sdk> {
    const url = HttpBinding.serviceUrl(baseUrl, sdkConstructor.address);
    return new sdkConstructor(new HttpBinding({ ...options, url }));
  }
}

async function throwFailure(response: Response, operation: string): Promise<never> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch (error) {
    throw new Error(`HTTP ${response.status} from ${operation} contains no gateway error`, { cause: error });
  }
  throw new GatewayHttpError(response.status, parseFailure(payload, operation));
}

function parseFailure(value: unknown, operation: string): GatewayFailure {
  if (!isObject(value) || typeof value.code !== "string" || typeof value.message !== "string") {
    throw new Error(`Response from ${operation} contains an invalid gateway error`);
  }
  return value as unknown as GatewayFailure;
}

function parseEvent(line: string, operation: string): Record<string, unknown> & { type: unknown } {
  let value: unknown;
  try {
    value = JSON.parse(line) as unknown;
  } catch (error) {
    throw new Error(`Stream from ${operation} contains invalid NDJSON`, { cause: error });
  }
  if (!isObject(value) || typeof value.type !== "string") {
    throw new Error(`Stream from ${operation} contains an invalid event`);
  }
  return value as Record<string, unknown> & { type: string };
}

async function* lines(stream: ReadableStream<Uint8Array>): AsyncIterable<string> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffered = "";
  try {
    while (true) {
      const result = await reader.read();
      if (result.done) break;
      buffered += decoder.decode(result.value, { stream: true });
      const complete = buffered.split("\n");
      buffered = complete.pop() ?? "";
      for (const line of complete) yield line;
    }
    buffered += decoder.decode();
    if (buffered.length > 0) yield buffered;
  } finally {
    reader.releaseLock();
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
