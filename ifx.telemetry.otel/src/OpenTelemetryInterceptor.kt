package ifx.telemetry.otel

@Deprecated(
    message = "Use OpenTelemetryRpcInterceptor",
    replaceWith = ReplaceWith("OpenTelemetryRpcInterceptor"),
)
typealias OpenTelemetryInterceptor = OpenTelemetryRpcInterceptor
