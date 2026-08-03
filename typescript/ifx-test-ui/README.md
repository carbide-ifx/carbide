# iFX test UI

Browser UI embedded by `ifx.protocol.rsocket` when a host enables `testUi`.
It reads the registered service catalog from `/ifx/services` and invokes the
services with the shared `@ifx/rpc-client` RSocket runtime.

The Kotlin host asset is checked in so normal Kotlin builds do not require
Node.js. After changing the UI, rebuild the embedded asset with:

```shell
npm --prefix ../ifx-rpc-client install
npm --prefix ../ifx-rpc-client run build
npm install
npm run build
```
