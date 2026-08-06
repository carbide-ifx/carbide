# iFX test UI

Browser UI embedded by `ifx.host` on the listener where host tooling is enabled.
It reads the registered service catalog from `/ifx/services` and invokes the
services with `@ifx/rpc-client-rsocket`.

The Kotlin host asset is checked in so normal Kotlin builds do not require
Node.js. After changing the UI, rebuild the embedded asset with:

```shell
cd typescript
npm install
npm run build
```

## Development without restarting the host

Configure the host to read the development bundle from disk:

```kotlin
Host(
    name = "Test System",
    listeners = listOf(
        ProtocolListener(
            protocol = RSocketServerProtocol(),
            port = 7070,
            tooling = HostTooling(
                developmentDirectory = "typescript/ifx-test-ui/dist",
            ),
        ),
    ),
)
```

Then keep this watcher running:

```shell
npm run dev
```

Edit `src/main.ts`. The host page reloads automatically after the watcher
finishes rebuilding the bundle, so Kotlin does not need to be rebuilt or
restarted.
