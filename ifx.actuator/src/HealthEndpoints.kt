package ifx.actuator

import ifx.host.HostExtension
import ifx.host.HostExtensionContext
import ifx.host.HostHealth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HealthEndpoints : HostExtension {
    override fun install(application: Application, context: HostExtensionContext) {
        application.routing {
            get("/ifx/health") {
                call.respondHealth(context.health(), healthy = true)
            }
            get("/ifx/health/ready") {
                val health = context.health()
                call.respondHealth(health, health.ready)
            }
            get("/ifx/health/live") {
                val health = context.health()
                call.respondHealth(health, health.live)
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondHealth(
    health: HostHealth,
    healthy: Boolean,
) {
    respondText(
        text = Json.encodeToString(health),
        contentType = ContentType.Application.Json,
        status = if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
    )
}
