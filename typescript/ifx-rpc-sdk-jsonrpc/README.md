# @ifx/rpc-sdk-jsonrpc

JSON-RPC 2.0 over HTTP transport for generated iFX TypeScript SDKs.

```ts
import { JsonRpcSdk } from "@ifx/rpc-sdk-jsonrpc";
import { IProductAccessSdk } from "./generated/IProductAccess";

const sdk = await JsonRpcSdk.connect(
  IProductAccessSdk,
  "http://localhost:7001",
  {
    headers: { tenant: "example" },
  },
);

const products = await sdk.filter({ ids: ["bike-1"] });
```

The transport uses the global Fetch API by default and accepts an alternative
`fetch` implementation. Fire-and-forget operations use JSON-RPC notifications.
Request streams fail explicitly because JSON-RPC over HTTP has no standard
streaming interaction.
