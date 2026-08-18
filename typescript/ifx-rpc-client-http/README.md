# `@ifx/rpc-client-http`

Conventional HTTP binding for an iFX gateway projection. It is intentionally
separate from `@ifx/rpc-client-jsonrpc`: URLs, envelopes, errors,
fire-and-forget responses, and streaming semantics differ.

```ts
import { HttpClient } from "@ifx/rpc-client-http";
import { ProductWebClient } from "./ProductWeb";

const client = await HttpClient.connect(ProductWebClient, "https://api.example.com", {
  requestHeaders: async () => ({ Authorization: `Bearer ${await accessToken()}` }),
});

const products = await client.productAccess.filter({ ids: ["42"] });
for await (const product of client.sales.listProducts()) {
  console.log(product);
}
```

The binding calls `POST /api/{surface}/{manager}/{operation}` and decodes
request streams as incremental `application/x-ndjson` gateway events.
