import { Buffer } from "./BufferPolyfill";
import {
  type Payload,
  type RSocket,
  RSocketConnector,
} from "rsocket-core";
import {
  decodeCompositeMetadata,
  encodeCompositeMetadata,
  encodeRoute,
  WellKnownMimeType,
} from "rsocket-composite-metadata";
import { WebsocketClientTransport } from "rsocket-websocket-client";
import {
  IfxBindingBase,
  decodeGatewayError,
  type IfxBindingOptions,
  type IfxOutboundCall,
  type IfxServiceConstructor,
  type IfxMessage,
} from "@carbide-ifx/rpc-sdk";

const IFX_HEADER_MIME_TYPE = "application/x-ifx-header";

export type RSocketBindingRuntimeOptions = IfxBindingOptions;

export interface RSocketBindingOptions extends RSocketBindingRuntimeOptions {
  readonly url: string;
  readonly keepAlive?: number;
  readonly lifetime?: number;
  readonly wsCreator?: (url: string) => WebSocket;
  /** Opaque data sent once in the RSocket SETUP frame, typically a short-lived credential. */
  readonly setupData?: string | (() => string | Promise<string>);
}

export class RSocketBinding extends IfxBindingBase {
  static async connect(options: RSocketBindingOptions): Promise<RSocketBinding> {
    const configuredSetupData = options.setupData;
    const setupData = typeof configuredSetupData === "function"
      ? await configuredSetupData()
      : configuredSetupData ?? '{ "data": "setup" }';
    const connector = new RSocketConnector({
      setup: {
        dataMimeType: WellKnownMimeType.APPLICATION_JSON.string,
        metadataMimeType: WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string,
        keepAlive: options.keepAlive,
        lifetime: options.lifetime,
        payload: { data: Buffer.from(setupData, "utf8") },
      },
      transport: new WebsocketClientTransport({
        url: options.url,
        wsCreator: options.wsCreator,
      }),
    });
    return new RSocketBinding(await connector.connect(), options);
  }

  static serviceUrl(baseUrl: string, serviceAddress: string): string {
    if (baseUrl.length === 0) throw new Error("The iFX RSocket base URL cannot be empty");
    if (serviceAddress.length === 0) throw new Error("The iFX service address cannot be empty");
    return `${baseUrl.replace(/\/+$/, "")}/${encodeURIComponent(serviceAddress)}`;
  }

  constructor(
    private readonly socket: RSocket,
    options: RSocketBindingRuntimeOptions = {},
  ) {
    super(options);
  }

  close(): void {
    this.socket.close();
  }

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
    await new Promise<void>((resolve, reject) => {
      this.socket.fireAndForget(toPayload(call), {
        onComplete: resolve,
        onError: (error) => reject(decodeGatewayError(error)),
      });
    });
  }

  private async *requestResponseExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const payload = await new Promise<Payload>((resolve, reject) => {
      let received = false;
      this.socket.requestResponse(toPayload(call), {
        onNext: (response) => {
          received = true;
          resolve(response);
        },
        onComplete: () => {
          if (!received) reject(new Error(`RSocket operation ${call.operation} completed without a payload`));
        },
        onError: (error) => reject(decodeGatewayError(error)),
        onExtension: () => {},
      });
    });
    yield fromPayload(payload);
  }

  private async *requestStreamExchange(call: IfxOutboundCall): AsyncIterable<IfxMessage> {
    const queue = new AsyncQueue<IfxMessage>();
    let ended = false;
    const stream = this.socket.requestStream(toPayload(call), 1, {
      onNext: (payload, isComplete) => {
        queue.push(fromPayload(payload));
        if (isComplete) {
          ended = true;
          queue.complete();
        }
      },
      onComplete: () => {
        ended = true;
        queue.complete();
      },
      onError: (error) => {
        ended = true;
        queue.fail(decodeGatewayError(error));
      },
      onExtension: () => {},
    });
    try {
      for await (const message of queue) {
        yield message;
        if (!ended) stream.request(1);
      }
    } finally {
      if (!ended) stream.cancel();
    }
  }
}

export class RSocketSdk {
  static async connect<Sdk>(
    sdkConstructor: IfxServiceConstructor<Sdk>,
    baseUrl: string,
    options: Omit<RSocketBindingOptions, "url"> = {},
  ): Promise<Sdk> {
    const url = RSocketBinding.serviceUrl(baseUrl, sdkConstructor.address);
    return new sdkConstructor(await RSocketBinding.connect({ ...options, url }));
  }
}

function toPayload(call: IfxOutboundCall): Payload {
  return {
    data: Buffer.from(call.message.body, "utf8"),
    metadata: encodeCompositeMetadata([
      [WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, encodeRoute(call.operation)],
      [IFX_HEADER_MIME_TYPE, Buffer.from(call.message.header, "utf8")],
    ]),
  };
}

function fromPayload(payload: Payload): IfxMessage {
  let header = "";
  if (payload.metadata) {
    for (const entry of decodeCompositeMetadata(payload.metadata)) {
      // ExplicitMimeTimeEntry exposes `type` at runtime in alpha.3 even though Entry declares `mimeType`.
      const mimeType = entry.mimeType ?? (entry as typeof entry & { type?: string }).type;
      if (mimeType === IFX_HEADER_MIME_TYPE) header = entry.content.toString("utf8");
    }
  }
  return {
    header,
    body: payload.data?.toString("utf8") ?? "",
  };
}

class AsyncQueue<Value> implements AsyncIterable<Value> {
  private readonly values: Value[] = [];
  private readonly waiters: Array<{
    resolve: (result: IteratorResult<Value>) => void;
    reject: (error: unknown) => void;
  }> = [];
  private ended = false;
  private failure?: unknown;

  push(value: Value): void {
    if (this.ended) return;
    const waiter = this.waiters.shift();
    if (waiter) waiter.resolve({ done: false, value });
    else this.values.push(value);
  }

  complete(): void {
    if (this.ended) return;
    this.ended = true;
    this.flush();
  }

  fail(error: unknown): void {
    if (this.ended) return;
    this.failure = error;
    this.ended = true;
    this.flush();
  }

  [Symbol.asyncIterator](): AsyncIterator<Value> {
    return {
      next: () => this.next(),
    };
  }

  private next(): Promise<IteratorResult<Value>> {
    const value = this.values.shift();
    if (value !== undefined) return Promise.resolve({ done: false, value });
    if (this.failure) return Promise.reject(this.failure);
    if (this.ended) return Promise.resolve({ done: true, value: undefined });
    return new Promise((resolve, reject) => this.waiters.push({ resolve, reject }));
  }

  private flush(): void {
    if (this.values.length > 0) return;
    for (const waiter of this.waiters.splice(0)) {
      if (this.failure) waiter.reject(this.failure);
      else waiter.resolve({ done: true, value: undefined });
    }
  }
}
