# `@ifx/rpc-sdk-http`

Conventional HTTP binding for an iFX gateway projection. It is intentionally
separate from `@ifx/rpc-sdk-jsonrpc`: URLs, envelopes, errors,
fire-and-forget responses, and streaming semantics differ.

```ts
import { HttpSdk } from "@ifx/rpc-sdk-http";
import { ProductWebSdk } from "./ProductWeb";

const sdk = await HttpSdk.connect(ProductWebSdk, "https://api.example.com", {
  requestHeaders: async () => ({ Authorization: `Bearer ${await accessToken()}` }),
});

const products = await sdk.productAccess.filter({ ids: ["42"] });
for await (const product of sdk.sales.listProducts()) {
  console.log(product);
}
```

The binding calls `POST /api/{surface}/{manager}/{operation}` and decodes
request streams as incremental `application/x-ndjson` gateway events.
