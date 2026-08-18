const test = require("node:test");
const assert = require("node:assert/strict");
const { Buffer } = require("buffer");
const {
  decodeCompositeMetadata,
  encodeCompositeMetadata,
  WellKnownMimeType,
} = require("rsocket-composite-metadata");
const { GatewayError } = require("@ifx/rpc-client");
const { RSocketBinding } = require("../dist");

globalThis.Buffer ??= Buffer;

const HEADER_MIME_TYPE = "application/x-ifx-header";

test("request-response uses IFX JSON and composite metadata", async () => {
  let requestPayload;
  const socket = fakeSocket({
    requestResponse(payload, subscriber) {
      requestPayload = payload;
      subscriber.onNext(
        {
          data: Buffer.from('{"ready":true,"live":true}'),
          metadata: responseMetadata('{"trace":"response"}'),
        },
        true,
      );
      return cancellable();
    },
  });
  const binding = new RSocketBinding(socket, {
    headers: async () => ({ trace: "request" }),
  });

  const response = await binding.requestResponse("status()");

  assert.deepEqual(response, { ready: true, live: true });
  assert.equal(requestPayload.data.toString("utf8"), "");
  assert.deepEqual(readMetadata(requestPayload.metadata), {
    route: "status()",
    header: '{"trace":"request"}',
  });
});

test("request stream applies backpressure and cancellation", async () => {
  const values = [1, 2, 3];
  let requested = 0;
  let cancelled = false;
  const socket = fakeSocket({
    requestStream(_payload, initialRequestN, subscriber) {
      let index = 0;
      const send = (amount) => {
        requested += amount;
        while (amount-- > 0 && index < values.length) {
          subscriber.onNext({ data: Buffer.from(String(values[index++])), metadata: responseMetadata("{}") }, false);
        }
      };
      send(initialRequestN);
      return {
        cancel() {
          cancelled = true;
        },
        request: send,
        onExtension() {},
      };
    },
  });
  const binding = new RSocketBinding(socket);
  const iterator = binding.requestStream("numbers()")[Symbol.asyncIterator]();

  assert.deepEqual(await iterator.next(), { done: false, value: 1 });
  assert.equal(requested, 1);
  assert.deepEqual(await iterator.next(), { done: false, value: 2 });
  assert.equal(requested, 2);
  await iterator.return();
  assert.equal(cancelled, true);
});

test("fire-and-forget resolves after the frame is sent", async () => {
  let requestPayload;
  const socket = fakeSocket({
    fireAndForget(payload, subscriber) {
      requestPayload = payload;
      subscriber.onComplete();
      return cancellable();
    },
  });
  const binding = new RSocketBinding(socket);

  await binding.fireAndForget("store(example.Product)", { id: "product-1" });

  assert.equal(requestPayload.data.toString("utf8"), '{"id":"product-1"}');
  assert.deepEqual(readMetadata(requestPayload.metadata), {
    route: "store(example.Product)",
    header: "{}",
  });
});

test("interceptors wrap request and response messages", async () => {
  let wireBody;
  const socket = fakeSocket({
    requestResponse(payload, subscriber) {
      wireBody = payload.data.toString("utf8");
      subscriber.onNext({ data: Buffer.from("20"), metadata: responseMetadata("{}") }, true);
      return cancellable();
    },
  });
  const interceptor = {
    async *intercept(call, next) {
      const changedCall = { ...call, message: { ...call.message, body: String(Number(call.message.body) * 2) } };
      for await (const response of next(changedCall)) {
        yield { ...response, body: String(Number(response.body) / 2) };
      }
    },
  };
  const binding = new RSocketBinding(socket, { interceptors: [interceptor] });

  assert.equal(await binding.requestResponse("double(kotlin.Int)", 5), 10);
  assert.equal(wireBody, "10");
});

test("service URL appends the qualified service address", () => {
  assert.equal(
    RSocketBinding.serviceUrl("ws://localhost:8080/", "access.product.contract.IProductAccess"),
    "ws://localhost:8080/access.product.contract.IProductAccess",
  );
});

test("canonical gateway error payloads are decoded across RSocket", async () => {
  const socket = fakeSocket({
    requestResponse(_payload, subscriber) {
      subscriber.onError(new Error('Application error: {"code":"forbidden","message":"Not allowed","details":{}}'));
      return cancellable();
    },
  });
  const binding = new RSocketBinding(socket);

  await assert.rejects(
    binding.requestResponse("productAccess/filter", {}),
    (error) => error instanceof GatewayError && error.failure.code === "forbidden",
  );
});

function fakeSocket(overrides) {
  return {
    close() {},
    onClose() {},
    fireAndForget() {
      throw new Error("Unexpected fireAndForget");
    },
    requestResponse() {
      throw new Error("Unexpected requestResponse");
    },
    requestStream() {
      throw new Error("Unexpected requestStream");
    },
    requestChannel() {
      throw new Error("Unexpected requestChannel");
    },
    metadataPush() {
      throw new Error("Unexpected metadataPush");
    },
    ...overrides,
  };
}

function cancellable() {
  return { cancel() {}, onExtension() {} };
}

function responseMetadata(header) {
  return encodeCompositeMetadata([[HEADER_MIME_TYPE, Buffer.from(header)]]);
}

function readMetadata(metadata) {
  const result = {};
  for (const entry of decodeCompositeMetadata(metadata)) {
    const mimeType = entry.mimeType ?? entry.type;
    if (mimeType === WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
      const routeLength = entry.content.readUInt8(0);
      result.route = entry.content.subarray(1, routeLength + 1).toString("utf8");
    }
    if (mimeType === HEADER_MIME_TYPE) result.header = entry.content.toString("utf8");
  }
  return result;
}
