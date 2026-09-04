import {
  type IfxBinding,
  type IfxBindingOptions,
  type IfxHeaderProvider,
  type IfxHeaders,
  type IfxInteraction,
  type IfxMessage,
  type IfxOutboundCall,
  type IfxOutboundInterceptorNext,
} from "./IfxBinding";

export abstract class IfxBindingBase implements IfxBinding {
  private readonly headers: IfxHeaders | IfxHeaderProvider;
  private readonly interceptors: IfxBindingOptions["interceptors"];

  protected constructor(options: IfxBindingOptions = {}) {
    this.headers = options.headers ?? {};
    this.interceptors = options.interceptors ?? [];
  }

  async fireAndForget(operation: string, request?: unknown): Promise<void> {
    const responses = this.invoke("fireAndForget", operation, arguments.length > 1, request);
    for await (const _response of responses) {
      throw new Error(`Fire-and-forget operation ${operation} unexpectedly returned a response`);
    }
  }

  async requestResponse<Response>(operation: string, request?: unknown): Promise<Response> {
    const responses = this.invoke("requestResponse", operation, arguments.length > 1, request);
    const iterator = responses[Symbol.asyncIterator]();
    try {
      const first = await iterator.next();
      if (first.done) throw new Error(`Request-response operation ${operation} returned no response`);
      const second = await iterator.next();
      if (!second.done) throw new Error(`Request-response operation ${operation} returned more than one response`);
      return decodeJson<Response>(first.value.body, operation);
    } finally {
      await iterator.return?.();
    }
  }

  async *requestStream<Response>(operation: string, request?: unknown): AsyncIterable<Response> {
    const responses = this.invoke("requestStream", operation, arguments.length > 1, request);
    for await (const response of responses) {
      yield decodeJson<Response>(response.body, operation);
    }
  }

  abstract close(): void;

  protected abstract exchange(call: IfxOutboundCall): AsyncIterable<IfxMessage>;

  private invoke(
    interaction: IfxInteraction,
    operation: string,
    hasRequest: boolean,
    request: unknown,
  ): AsyncIterable<IfxMessage> {
    const self = this;
    return (async function* invokeWithHeaders() {
      const headerValues = typeof self.headers === "function" ? await self.headers() : self.headers;
      const body = hasRequest ? encodeJson(request, operation) : "";
      const call: IfxOutboundCall = {
        interaction,
        operation,
        message: { header: JSON.stringify(headerValues), body },
      };
      let next: IfxOutboundInterceptorNext = self.exchange.bind(self);
      for (const interceptor of [...(self.interceptors ?? [])].reverse()) {
        const following = next;
        next = (interceptedCall) => interceptor.intercept(interceptedCall, following);
      }
      yield* next(call);
    })();
  }
}

function encodeJson(value: unknown, operation: string): string {
  const encoded = JSON.stringify(value);
  if (encoded === undefined) {
    throw new Error(`Request for ${operation} cannot be represented as JSON`);
  }
  return encoded;
}

function decodeJson<Value>(value: string, operation: string): Value {
  try {
    return JSON.parse(value) as Value;
  } catch (error) {
    throw new Error(`Response from ${operation} is not valid JSON`, { cause: error });
  }
}
