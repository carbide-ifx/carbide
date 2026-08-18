import {
  IfxBindingBase,
  type IfxBindingOptions,
  type IfxOutboundCall,
  type IfxServiceConstructor,
  type IfxMessage,
} from "@ifx/rpc-sdk";

const IFX_HEADERS = "Ifx-Message-Headers";

export interface JsonRpcBindingOptions extends IfxBindingOptions {
  readonly url: string;
  readonly fetch?: typeof globalThis.fetch;
}

export class JsonRpcError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly data?: unknown,
  ) {
    super(message);
    this.name = "JsonRpcError";
  }
}

export class JsonRpcBinding extends IfxBindingBase {
  static serviceUrl(baseUrl: string, serviceAddress: string): string {
    if (baseUrl.length === 0) throw new Error("The iFX JSON-RPC base URL cannot be empty");
    if (serviceAddress.length === 0) throw new Error("The iFX service address cannot be empty");
    return `${baseUrl.replace(/\/+$/, "")}/${encodeURIComponent(serviceAddress)}`;
  }

  private readonly fetch: typeof globalThis.fetch;
  private nextId = 1;

  constructor(private readonly options: JsonRpcBindingOptions) {
    super(options);
    this.fetch = options.fetch ?? globalThis.fetch;
    if (!this.fetch) throw new Error("JSON-RPC requires a Fetch API implementation");
  }

  close(): void {}

  protected exchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    switch (call.interaction) {
      case "fireAndForget":
        return this.notificationExchange(call);
      case "requestResponse":
        return this.requestResponseExchange(call);
      case "requestStream":
        return this.unsupportedStream(call);
    }
  }

  private async *notificationExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const response = await this.send(call);
    if (!response.ok) {
      throw new Error(`JSON-RPC notification ${call.operation} failed with HTTP ${response.status}`);
    }
  }

  private async *requestResponseExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const id = this.nextId++;
    const response = await this.send(call, id);
    const payload = await parseResponse(response, call.operation);
    if (payload.jsonrpc !== "2.0" || payload.id !== id) {
      throw new Error(`Response from ${call.operation} is not a matching JSON-RPC 2.0 response`);
    }
    if (payload.error !== undefined) {
      if (!isObject(payload.error) || typeof payload.error.code !== "number" || typeof payload.error.message !== "string") {
        throw new Error(`Response from ${call.operation} contains an invalid JSON-RPC error`);
      }
      throw new JsonRpcError(payload.error.code, payload.error.message, payload.error.data);
    }
    if (!("result" in payload)) {
      throw new Error(`Response from ${call.operation} contains no JSON-RPC result`);
    }
    const encodedHeaders = response.headers.get(IFX_HEADERS);
    yield {
      header: encodedHeaders === null ? "{}" : decodeBase64(encodedHeaders),
      body: JSON.stringify(payload.result),
    };
  }

  private async *unsupportedStream(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    throw new Error(`Streaming is not supported by JSON-RPC over HTTP: ${call.operation}`);
  }

  private send(call: IfxOutboundCall, id?: number): Promise<Response> {
    const request: Record<string, unknown> = {
      jsonrpc: "2.0",
      method: call.operation,
    };
    if (call.message.body.length > 0) request.params = parseRequestBody(call.message.body, call.operation);
    if (id !== undefined) request.id = id;

    return this.fetch(this.options.url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [IFX_HEADERS]: encodeBase64(call.message.header),
      },
      body: JSON.stringify(request),
    });
  }
}

export class JsonRpcSdk {
  static async connect<Sdk>(
    sdkConstructor: IfxServiceConstructor<Sdk>,
    baseUrl: string,
    options: Omit<JsonRpcBindingOptions, "url"> = {},
  ): Promise<Sdk> {
    const url = JsonRpcBinding.serviceUrl(baseUrl, sdkConstructor.address);
    return new sdkConstructor(new JsonRpcBinding({ ...options, url }));
  }
}

function parseRequestBody(body: string, operation: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch (error) {
    throw new Error(`Request for ${operation} is not valid JSON`, { cause: error });
  }
}

async function parseResponse(response: Response, operation: string): Promise<Record<string, unknown>> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch (error) {
    throw new Error(`Response from ${operation} is not valid JSON`, { cause: error });
  }
  if (!isObject(payload)) throw new Error(`Response from ${operation} is not a JSON-RPC object`);
  return payload;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function encodeBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function decodeBase64(value: string): string {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
