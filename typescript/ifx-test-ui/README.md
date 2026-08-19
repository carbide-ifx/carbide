# iFX test UI

Browser UI provided by the `ifx.service-explorer` module. Its `ServiceExplorer`
serves bundled resources by default, delegates an optional development-directory
override to the general `ifx.host.webapp` host, reads the registered service
catalog from `IActuator.catalog()`, and invokes services with
`@ifx/rpc-sdk-rsocket`. There is no separate HTTP catalog endpoint.

The Service Explorer is a normal npm web application. Its build writes
`index.html` and the bundled JavaScript to `dist/`, synchronizes JVM resources,
and generates the compressed Native asset projection in `ifx.service-explorer`.
Rebuild after changing the frontend:

```shell
cd typescript
npm install
npm run build
```

## Development

Custom hosts can configure `ServiceExplorer` to read the development bundle from
disk instead of its bundled resources:

```kotlin
import ifx.service.explorer.ServiceExplorer

install(
    ServiceExplorer(
        listener = rsocket,
        developmentDirectory = "typescript/ifx-test-ui/dist",
    ),
)
```

Then keep this watcher running:

```shell
npm run dev
```

Edit `src/main.ts` or `index.html`. The watcher updates `dist/`, and Ktor serves
the next request from that directory without rebuilding or restarting Kotlin.
Refresh the browser after a rebuild.
