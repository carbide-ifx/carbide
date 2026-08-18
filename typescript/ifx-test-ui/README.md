# iFX test UI

Browser UI provided by the optional `ifx.service-explorer` module. Its
`ServiceExplorer` composes the general `ifx.host.webapp` host, reads the
registered service catalog from `IActuator.catalog()`, and invokes services with
`@ifx/rpc-sdk-rsocket`. There is no separate HTTP catalog endpoint.

The Service Explorer is a normal npm web application. Its build writes
`index.html` and the bundled JavaScript to `dist/`; no web assets are generated
into Kotlin source. Build it before starting or packaging a host that serves it:

```shell
cd typescript
npm install
npm run build
```

## Development

Configure the host to read the development bundle from disk:

```kotlin
import ifx.subsystem.default

Host.default(
    name = "Test System",
    rsocketPort = 7070,
    serviceExplorerDirectory = "typescript/ifx-test-ui/dist",
)
```

Then keep this watcher running:

```shell
npm run dev
```

Edit `src/main.ts` or `index.html`. The watcher updates `dist/`, and Ktor serves
the next request from that directory without rebuilding or restarting Kotlin.
Refresh the browser after a rebuild.
