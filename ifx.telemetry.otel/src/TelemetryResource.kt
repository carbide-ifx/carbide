package ifx.telemetry.otel

internal const val TELEMETRY_SDK_VERSION = "0.1.0"
internal const val INSTRUMENTATION_SCOPE_NAME = "ifx.telemetry.otel"

/** Immutable identity shared by telemetry emitted from one service instance. */
class TelemetryResource(
    val serviceName: String,
    val serviceNamespace: String? = null,
    val serviceVersion: String? = null,
    val serviceInstanceId: String? = null,
    val deploymentEnvironmentName: String? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    private val applicationAttributes: Map<String, String> = attributes.toMap()

    /** Application-defined attributes; typed identity fields take precedence during export. */
    val attributes: Map<String, String> get() = applicationAttributes.toMap()

    init {
        require(serviceName.isNotBlank()) { "serviceName must not be blank" }
        require(serviceNamespace == null || serviceNamespace.isNotBlank()) {
            "serviceNamespace must not be blank"
        }
        require(serviceVersion == null || serviceVersion.isNotBlank()) { "serviceVersion must not be blank" }
        require(serviceInstanceId == null || serviceInstanceId.isNotBlank()) {
            "serviceInstanceId must not be blank"
        }
        require(deploymentEnvironmentName == null || deploymentEnvironmentName.isNotBlank()) {
            "deploymentEnvironmentName must not be blank"
        }
        require(applicationAttributes.keys.none(String::isBlank)) { "resource attribute keys must not be blank" }
    }

    internal fun otelAttributes(): Map<String, String> = buildMap {
        putAll(applicationAttributes)
        put("service.name", serviceName)
        serviceNamespace?.let { put("service.namespace", it) }
        serviceVersion?.let { put("service.version", it) }
        serviceInstanceId?.let { put("service.instance.id", it) }
        deploymentEnvironmentName?.let { put("deployment.environment.name", it) }
    }

    override fun equals(other: Any?): Boolean = other is TelemetryResource &&
        serviceName == other.serviceName &&
        serviceNamespace == other.serviceNamespace &&
        serviceVersion == other.serviceVersion &&
        serviceInstanceId == other.serviceInstanceId &&
        deploymentEnvironmentName == other.deploymentEnvironmentName &&
        applicationAttributes == other.applicationAttributes

    override fun hashCode(): Int {
        var result = serviceName.hashCode()
        result = 31 * result + (serviceNamespace?.hashCode() ?: 0)
        result = 31 * result + (serviceVersion?.hashCode() ?: 0)
        result = 31 * result + (serviceInstanceId?.hashCode() ?: 0)
        result = 31 * result + (deploymentEnvironmentName?.hashCode() ?: 0)
        result = 31 * result + applicationAttributes.hashCode()
        return result
    }

    override fun toString(): String = "TelemetryResource(" +
        "serviceName=$serviceName, " +
        "serviceNamespace=$serviceNamespace, " +
        "serviceVersion=$serviceVersion, " +
        "serviceInstanceId=$serviceInstanceId, " +
        "deploymentEnvironmentName=$deploymentEnvironmentName, " +
        "attributes=$applicationAttributes)"
}
