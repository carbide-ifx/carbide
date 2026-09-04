const test = require("node:test");
const assert = require("node:assert/strict");
const { Buffer } = require("node:buffer");
const { JsonRpcBinding, JsonRpcSdk, JsonRpcError } = require("../dist");

const IFX_HEADERS = "Ifx-Message-Headers";

test("request-response uses JSON-RPC 2.0 and IFX headers", async () => {
  let requestedUrl;
  let request;
  let requestHeaders;
  const fetch = async (url, init) => {
    requestedUrl = url;
    request = JSON.parse(init.body);
    requestHeaders = init.headers;
    return new Response(
      JSON.stringify({ jsonrpc: "2.0", result: { ready: true }, id: request.id }),
      {
        headers: {
          "Content-Type": "application/json",
          [IFX_HEADERS]: encodeHeaders({ trace: "response-æ" }),
        },
      },
    );
  };
  class StatusServiceSdk {
    static address = "example.StatusService";
    constructor(binding) {
      this.binding = binding;
    }
    status() {
      return this.binding.requestResponse("status()");
    }
  }

  const sdk = await JsonRpcSdk.connect(StatusServiceSdk, "http://localhost:8081/", {
    fetch,
    headers: { trace: "request-ø" },
  });

  assert.deepEqual(await sdk.status(), { ready: true });
  assert.equal(requestedUrl, "http://localhost:8081/example.StatusService");
  assert.deepEqual(request, { jsonrpc: "2.0", method: "status()", id: 1 });
  assert.deepEqual(decodeHeaders(requestHeaders[IFX_HEADERS]), { trace: "request-ø" });
});

test("fire-and-forget sends a JSON-RPC notification", async () => {
  let request;
  const binding = new JsonRpcBinding({
    url: "http://localhost:8081/example.Commands",
    fetch: async (_url, init) => {
      request = JSON.parse(init.body);
      return new Response(null, { status: 204 });
    },
  });

  await binding.fireAndForget("notify(kotlin.String)", "product-1");

  assert.deepEqual(request, {
    jsonrpc: "2.0",
    method: "notify(kotlin.String)",
    params: "product-1",
  });
  assert.equal("id" in request, false);
});

test("JSON-RPC errors retain their code and data", async () => {
  const binding = new JsonRpcBinding({
    url: "http://localhost:8081/example.StatusService",
    fetch: async (_url, init) => {
      const request = JSON.parse(init.body);
      return new Response(
        JSON.stringify({
          jsonrpc: "2.0",
          error: { code: -32601, message: "Method not found", data: { method: request.method } },
          id: request.id,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    },
  });

  await assert.rejects(
    binding.requestResponse("missing()"),
    (error) => error instanceof JsonRpcError &&
      error.code === -32601 &&
      error.data.method === "missing()",
  );
});

test("request streams fail without making an HTTP request", async () => {
  let requests = 0;
  const binding = new JsonRpcBinding({
    url: "http://localhost:8081/example.Streams",
    fetch: async () => {
      requests += 1;
      throw new Error("Unexpected request");
    },
  });

  await assert.rejects(async () => {
    for await (const _value of binding.requestStream("values()")) {}
  }, /Streaming is not supported/);
  assert.equal(requests, 0);
});

function encodeHeaders(headers) {
  return Buffer.from(JSON.stringify(headers), "utf8").toString("base64");
}

function decodeHeaders(headers) {
  return JSON.parse(Buffer.from(headers, "base64").toString("utf8"));
}
