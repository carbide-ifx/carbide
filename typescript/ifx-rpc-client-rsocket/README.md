# @ifx/rpc-client-rsocket

RSocket over WebSocket client for generated iFX TypeScript service clients.

```ts
import { RSocketClient } from "@ifx/rpc-client-rsocket";
import { ISalesManagerClient } from "./generated/ISalesManager";

const client = await RSocketClient.connect(
  ISalesManagerClient,
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
  for await (const product of client.listProducts()) {
    console.log(product);
  }
} finally {
  client.close();
}
```

The RSocket dependencies are pinned to `1.0.0-alpha.3`, which is still an
upstream alpha API. Browsers use the global `WebSocket` constructor; other
runtimes can provide `wsCreator`.
