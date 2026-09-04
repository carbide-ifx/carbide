package ifx.build.jib

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/** A filesystem directory copied verbatim into the container image. */
@Configurable
interface JibExtraDirectory {
    /** Directory produced by another build, such as an npm `dist` directory. */
    val source: Path

    /** Absolute directory path inside the container. */
    val destination: String
}

/** Settings for turning a runnable JVM subsystem into a container image. */
@Configurable
interface JibSettings {
    /** Target image name, including registry and tag when they differ from Docker Hub and `latest`. */
    val image: String

    /** Java runtime image used as the base layer. */
    val baseImage: String
        get() = "gcr.io/distroless/java21-debian12:nonroot"

    /** Additional JVM arguments baked into the image entrypoint. */
    val jvmArgs: List<String>
        get() = emptyList()

    /** Additional tags applied beside the tag in [image]. */
    val tags: List<String>
        get() = emptyList()

    /** TCP ports documented in the image metadata. Port publication remains a runtime concern. */
    val ports: List<Int>
        get() = emptyList()

    /** Environment variables baked into the image metadata. */
    val environment: Map<String, String>
        get() = emptyMap()

    /** OCI labels baked into the image metadata. */
    val labels: Map<String, String>
        get() = emptyMap()

    /** Built directories copied as separate filesystem layers instead of JAR resources. */
    val extraDirectories: List<JibExtraDirectory>
        get() = emptyList()

    /** Optional Docker credential helper used to pull [baseImage]. */
    val baseCredentialHelper: String?

    /** Optional Docker credential helper used to push [image]. */
    val targetCredentialHelper: String?
}
