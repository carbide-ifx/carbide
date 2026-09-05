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
  `ifx.proxy.factory`, `ifx.gateway`,
  `ifx.gateway.ktor`, `ifx.gateway.typescript`, `ifx.actuator`, `ifx.service.explorer`,
  `ifx.telemetry.otel` and `ifx.subsystem`.
- Build-time libraries: `ifx.contract.ksp`, `ifx.rpc.schema.ksp`, `ifx.subsystem.ksp`,
  `ifx.rpc.compiler`, and `ifx.rpc.typescript.ksp`.
- Supporting libraries: `ifx.test` and `ifx.stdlib`.

## Deliberately excluded

- `test.*` modules are executable specifications and examples, not supported dependencies.
- `ifx.build.gateway` and `ifx.build.jib` are Kotlin Toolchain plugins rather than Maven library
  modules. Their distribution needs a separate plugin publication path.
- TypeScript packages under `typescript/` use npm publication rather than Maven Central.

## npm packages

The public npm surface is `@carbide-ifx/rpc-sdk`, `@carbide-ifx/rpc-sdk-rsocket`,
`@carbide-ifx/rpc-sdk-jsonrpc`, and `@carbide-ifx/rpc-sdk-http`. Publish the protocol-neutral runtime
first because every transport depends on that exact version.

## Release command

After the Central namespace and credentials are configured, publish a manually reviewed deployment:

```shell
./kotlin publish mavenCentral
```

Use `./kotlin publish local` to validate the complete signed publication locally. Keep these
commands unqualified: Kotlin Toolchain `0.12.0-dev-4235` correctly discovers all enabled publication
tasks, while its module-filtered Maven Central path does not recognize the built-in Central Portal
repository.

The default Maven Central publishing mode is manual. Do not enable automatic release until a manual
deployment has been validated in the Central Portal.

After authenticating npm with access to the `@carbide-ifx` scope, publish the TypeScript runtime and
transports:

```shell
cd typescript
npm publish --workspace @carbide-ifx/rpc-sdk
npm publish --workspace @carbide-ifx/rpc-sdk-rsocket
npm publish --workspace @carbide-ifx/rpc-sdk-jsonrpc
npm publish --workspace @carbide-ifx/rpc-sdk-http
```
