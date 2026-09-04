package ifx.telemetry.otel.ktor.client

import ifx.telemetry.otel.trace.SpanKind
import ifx.telemetry.otel.trace.Tracer
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder

class OpenTelemetryClientPluginConfig {
    var tracer: Tracer? = null

    /** Excludes requests such as health checks or telemetry export from instrumentation. */
    var shouldInstrument: (HttpRequestBuilder) -> Boolean = { true }
}

/** Creates one client span for each logical Ktor request on clients where this plugin is installed. */
val OpenTelemetryClientPlugin = createClientPlugin(
    name = "IfxOpenTelemetryClientPlugin",
    createConfiguration = ::OpenTelemetryClientPluginConfig,
) {
    val tracer = requireNotNull(pluginConfig.tracer) {
        "OpenTelemetryClientPlugin requires a tracer"
    }
    val shouldInstrument = pluginConfig.shouldInstrument

    on(Send) { request ->
        if (!shouldInstrument(request)) return@on proceed(request)

        tracer.span(
            name = request.method.value,
            kind = SpanKind.CLIENT,
            attributes = request.spanAttributes(),
        ) {
            request.headers.remove(TRACEPARENT_HEADER)
            request.headers.append(TRACEPARENT_HEADER, context.traceParent)
            request.headers.remove(TRACESTATE_HEADER)
            context.traceState?.let { request.headers.append(TRACESTATE_HEADER, it) }

            proceed(request).also { call ->
                val status = call.response.status.value
                setAttribute("http.response.status_code", status)
                if (status >= 400) setAttribute("error.type", status)
            }
        }
    }
}

private fun HttpRequestBuilder.spanAttributes(): Map<String, String> = buildMap {
    put("http.request.method", method.value)
    url.host.takeIf(String::isNotBlank)?.let { put("server.address", it) }
    url.port.takeIf { it > 0 && it != url.protocol.defaultPort }
        ?.let { put("server.port", it.toString()) }
    put("url.scheme", url.protocol.name)
}

private const val TRACEPARENT_HEADER = "traceparent"
private const val TRACESTATE_HEADER = "tracestate"
