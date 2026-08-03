export interface IfxBinding {
  fireAndForget(operation: string, request?: unknown): Promise<void>;

  requestResponse<Response>(operation: string, request?: unknown): Promise<Response>;

  requestStream<Response>(operation: string, request?: unknown): AsyncIterable<Response>;

  close(): void;
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
