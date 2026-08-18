export interface IfxBinding {
  fireAndForget(operation: string, request?: unknown): Promise<void>;

  requestResponse<Response>(operation: string, request?: unknown): Promise<Response>;

  requestStream<Response>(operation: string, request?: unknown): AsyncIterable<Response>;

  close(): void;
}

export interface IfxClientConstructor<Client> {
  readonly address: string;
  new(binding: IfxBinding): Client;
}

export interface IfxMessage {
  readonly header: string;
  readonly body: string;
}

export type IfxInteraction = "fireAndForget" | "requestResponse" | "requestStream";

export interface IfxClientCall {
  readonly interaction: IfxInteraction;
  readonly operation: string;
  readonly message: IfxMessage;
}

export type IfxClientInterceptorNext = (call: IfxClientCall) => AsyncIterable<IfxMessage>;

export interface IfxClientInterceptor {
  intercept(call: IfxClientCall, next: IfxClientInterceptorNext): AsyncIterable<IfxMessage>;
}

export type IfxHeaders = Readonly<Record<string, unknown>>;
export type IfxHeaderProvider = () => IfxHeaders | Promise<IfxHeaders>;

export interface IfxClientBindingOptions {
  readonly headers?: IfxHeaders | IfxHeaderProvider;
  readonly interceptors?: readonly IfxClientInterceptor[];
}

export interface GatewayFailure {
  readonly code: string;
  readonly message: string;
  readonly details?: Readonly<Record<string, string>>;
}

export class GatewayError extends Error {
  constructor(readonly failure: GatewayFailure, options?: ErrorOptions) {
    super(failure.message, options);
    this.name = "GatewayError";
  }
}

/** Converts a transport error containing a canonical gateway failure, leaving other errors intact. */
export function decodeGatewayError(error: unknown): unknown {
  const message = error instanceof Error ? error.message : typeof error === "string" ? error : undefined;
  if (message === undefined) return error;
  const start = message.indexOf("{");
  const end = message.lastIndexOf("}");
  if (start < 0 || end < start) return error;
  try {
    const value: unknown = JSON.parse(message.slice(start, end + 1));
    if (!isGatewayFailure(value)) return error;
    return new GatewayError(value, { cause: error });
  } catch {
    return error;
  }
}

function isGatewayFailure(value: unknown): value is GatewayFailure {
  return typeof value === "object" && value !== null &&
    "code" in value && typeof value.code === "string" &&
    "message" in value && typeof value.message === "string";
}
