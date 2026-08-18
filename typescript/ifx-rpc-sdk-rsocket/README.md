# @ifx/rpc-sdk-rsocket

RSocket over WebSocket transport for generated iFX TypeScript SDKs.

```ts
import { RSocketSdk } from "@ifx/rpc-sdk-rsocket";
import { ISalesManagerSdk } from "./generated/ISalesManager";

const sdk = await RSocketSdk.connect(
  ISalesManagerSdk,
  "ws://localhost:7000",
  {
    headers: () => ({
      "ifx.context": {
        "example.caller": { subject: "user-42" },
      },
    }),
  },
);

try {
  for await (const product of sdk.listProducts()) {
    console.log(product);
  }
} finally {
  sdk.close();
}
```

The RSocket dependencies are pinned to `1.0.0-alpha.3`, which is still an
upstream alpha API. Browsers use the global `WebSocket` constructor; other
runtimes can provide `wsCreator`.
