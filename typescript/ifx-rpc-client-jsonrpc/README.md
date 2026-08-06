# @ifx/rpc-client-jsonrpc

JSON-RPC 2.0 over HTTP client for generated iFX TypeScript service clients.

```ts
import { JsonRpcClient } from "@ifx/rpc-client-jsonrpc";
import { IProductAccessClient } from "./generated/IProductAccess";

const client = await JsonRpcClient.connect(
  IProductAccessClient,
  "http://localhost:7001",
  {
    headers: { tenant: "example" },
  },
);

const products = await client.filter({ ids: ["bike-1"] });
```

The client uses the global Fetch API by default and accepts an alternative
`fetch` implementation. Fire-and-forget operations use JSON-RPC notifications.
Request streams fail explicitly because JSON-RPC over HTTP has no standard
streaming interaction.
