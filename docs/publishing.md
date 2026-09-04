# Publishing Carbide

Publication is opt-in. A module is part of the supported Maven surface only when its `module.yaml`
applies `publishing.module-template.yaml`. The template supplies the shared `io.carbide-ifx`
coordinates, Maven Central signing, sources, license, developer, website, and SCM metadata. Each
published module supplies its own description.

## Maven Central modules

The public Maven surface is:

- Core contracts: `ifx.context`, `ifx.logging`, `ifx.service`, `ifx.protocol.contract`,
  `ifx.host.contract`, and `ifx.gateway.contract`.
- Runtime: `ifx.host`, `ifx.host.webapp`, `ifx.protocol.jsonrpc`, `ifx.protocol.rsocket`,
  `ifx.proxy-factory`, `ifx.proxy-factory.jsonrpc`, `ifx.proxy-factory.rsocket`, `ifx.gateway`,
  `ifx.gateway.ktor`, `ifx.gateway.typescript`, `ifx.actuator`, `ifx.service-explorer`,
  `ifx.telemetry.otel`, `ifx.telemetry.otel.ktor-client`, and `ifx.subsystem`.
- Build-time libraries: `ifx.contract.ksp`, `ifx.rpc.schema.ksp`, `ifx.subsystem.ksp`,
  `ifx.rpc.compiler-plugin`, and `ifx.rpc.typescript.ksp`.
- Supporting libraries: `ifx.testing`, `utility.stdlib`, and `utility.db`.

## Deliberately excluded

- `test.*` modules are executable specifications and examples, not supported dependencies.
- `terpal.compiler-plugin` is a local bootstrap artifact. It publishes only to Maven Local and is
  not part of Carbide's Central namespace.
- `ifx.gateway.artifacts` and `ifx.jib` are Kotlin Toolchain plugins rather than Maven library
  modules. Their distribution needs a separate plugin publication path.
- TypeScript packages under `typescript/` use npm publication and are versioned separately.
- The legacy `utility.event` directory is not part of the Kotlin Toolchain project.

## Release command

After the Central namespace and credentials are configured, publish a manually reviewed deployment:

```shell
./terpal.compiler-plugin/publish-local
./kotlin publish mavenCentral
```

The default Maven Central publishing mode is manual. Do not enable automatic release until a manual
deployment has been validated in the Central Portal.
