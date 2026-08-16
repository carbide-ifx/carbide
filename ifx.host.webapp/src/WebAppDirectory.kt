package ifx.host.webapp

import io.ktor.server.application.Application

internal expect fun Application.installWebAppDirectory(
    directory: String,
    mountPath: String,
    indexFile: String,
)
