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

## Development without restarting the host

Configure the host to read the development bundle from disk:

```kotlin
Host(
    port = 7070,
    name = "Test System",
    testUi = true,
    testUiDevelopmentDirectory = "typescript/ifx-test-ui/dist",
)
```

Then keep this watcher running:

```shell
npm run dev
```

Edit `src/main.ts`. The host page reloads automatically after the watcher
finishes rebuilding the bundle, so Kotlin does not need to be rebuilt or
restarted.
