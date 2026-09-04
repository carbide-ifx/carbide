const assert = require("node:assert/strict");
const test = require("node:test");
const { GatewayHttpError, HttpBinding } = require("../dist");

test("request-response uses the conventional projected operation path", async () => {
  let request;
  const binding = new HttpBinding({
    url: "https://example.test/api/product-web",
    requestHeaders: { Authorization: "Bearer token" },
    fetch: async (url, options) => {
      request = { url, options };
      return new Response('{"accepted":true}', {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  });

  const result = await binding.requestResponse("productAccess/filter", { ids: ["42"] });

  assert.deepEqual(result, { accepted: true });
  assert.equal(request.url, "https://example.test/api/product-web/productAccess/filter");
  assert.equal(request.options.headers.get("Authorization"), "Bearer token");
  assert.equal(request.options.body, '{"ids":["42"]}');
});

test("request-stream decodes NDJSON across transport chunks", async () => {
  const encoder = new TextEncoder();
  const body = new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode('{"type":"next","data":{"id":'));
      controller.enqueue(encoder.encode('"42"}}\n{"type":"complete"}\n'));
      controller.close();
    },
  });
  const binding = new HttpBinding({
    url: "https://example.test/api/product-web",
    fetch: async () => new Response(body, { status: 200 }),
  });

  const values = [];
  for await (const value of binding.requestStream("sales/listProducts")) values.push(value);

  assert.deepEqual(values, [{ id: "42" }]);
});

test("gateway error objects retain status and stable failure code", async () => {
  const binding = new HttpBinding({
    url: "https://example.test/api/product-web",
    fetch: async () => new Response('{"code":"unauthorized","message":"Authentication required"}', {
      status: 401,
      headers: { "Content-Type": "application/json" },
    }),
  });

  await assert.rejects(
    binding.requestResponse("productAccess/filter", {}),
    (error) => error instanceof GatewayHttpError && error.status === 401 && error.failure.code === "unauthorized",
  );
});
