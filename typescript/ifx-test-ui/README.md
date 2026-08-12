# iFX test UI

Browser UI provided by the optional `ifx.host.tooling` module. Its
`ServiceExplorer` composes the general `ifx.host.webapp` host, reads the
registered service catalog from `IActuator.catalog()`, and invokes services with
`@ifx/rpc-client-rsocket`. There is no separate HTTP catalog endpoint.

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
import ifx.subsystem.subsystem

Host.subsystem(name = "Test System") {
    val rsocket = listen(RSocketServerProtocol(), port = 7070)
    install(
        ServiceExplorer(
            listener = rsocket,
            developmentDirectory = "typescript/ifx-test-ui/dist",
        ),
    )
}
```

Then keep this watcher running:

```shell
npm run dev
```

Edit `src/main.ts`. The host page reloads automatically after the watcher
finishes rebuilding the bundle, so Kotlin does not need to be rebuilt or
restarted.
