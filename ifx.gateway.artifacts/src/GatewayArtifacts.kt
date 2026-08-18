package ifx.build.gateway

import ifx.gateway.contract.GatewayProjection
import ifx.gateway.contract.GatewayProjectionProvider
import ifx.gateway.ktor.GatewayHttpDeployment
import ifx.gateway.ktor.renderOpenApi
import ifx.gateway.typescript.renderTypeScriptClient
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TaskAction
fun generateGatewayArtifacts(
    @Input runtimeClasspath: Classpath,
    @Input applicationJar: CompilationArtifact,
    @Output outputDirectory: Path,
) {
    val applicationJarPath = applicationJar.artifact
    val classpath = (runtimeClasspath.resolvedFiles + applicationJarPath).distinct()
    val classLoader = URLClassLoader(
        classpath.map { path -> path.toUri().toURL() }.toTypedArray(),
        GatewayProjectionProvider::class.java.classLoader,
    )
    val projections = classLoader.use { loader ->
        projectionProviderNames(applicationJarPath).flatMap { providerName ->
            val provider = Class.forName(providerName, true, loader)
                .getDeclaredConstructor()
                .newInstance() as GatewayProjectionProvider
            provider.projections
        }
    }
    require(projections.isNotEmpty()) {
        "No gateway projections were declared by ${applicationJarPath.fileName}"
    }

    writeGatewayArtifacts(outputDirectory, renderGatewayArtifacts(projections))
    println(outputDirectory.toAbsolutePath())
}

internal data class GatewayArtifact(
    val relativePath: Path,
    val content: String,
)

internal fun renderGatewayArtifacts(projections: List<GatewayProjection>): List<GatewayArtifact> {
    val duplicateAddress = projections
        .groupingBy(GatewayProjection::address)
        .eachCount()
        .entries
        .firstOrNull { entry -> entry.value > 1 }
    require(duplicateAddress == null) {
        "Duplicate gateway projection address: ${duplicateAddress?.key}"
    }

    return projections.sortedBy(GatewayProjection::address).flatMap { projection ->
        val directory = projection.version
            ?.let { version -> Path.of(projection.name, "v$version") }
            ?: Path.of(projection.name)
        val apiVersion = "${projection.version ?: 1}.0.0"
        listOf(
            GatewayArtifact(
                relativePath = directory.resolve("client.ts"),
                content = projection.renderTypeScriptClient(),
            ),
            GatewayArtifact(
                relativePath = directory.resolve("openapi.json"),
                content = projection.renderOpenApi(
                    deployment = GatewayHttpDeployment(apiVersion = apiVersion),
                ),
            ),
        )
    }
}

internal fun writeGatewayArtifacts(outputDirectory: Path, artifacts: List<GatewayArtifact>) {
    val normalizedOutputDirectory = outputDirectory.toAbsolutePath().normalize()
    val outputs = artifacts.map { artifact ->
        val output = normalizedOutputDirectory.resolve(artifact.relativePath).normalize()
        require(output.startsWith(normalizedOutputDirectory)) {
            "Gateway artifact path escapes its output directory: ${artifact.relativePath}"
        }
        artifact to output
    }
    if (Files.exists(outputDirectory)) {
        check(outputDirectory.toFile().deleteRecursively()) {
            "Could not replace gateway artifact directory: ${outputDirectory.toAbsolutePath()}"
        }
    }
    outputDirectory.createDirectories()
    outputs.forEach { (artifact, output) ->
        output.parent.createDirectories()
        output.writeText(artifact.content)
    }
}

private fun projectionProviderNames(applicationJar: Path): List<String> =
    JarFile(applicationJar.toFile()).use { jar ->
        val entry = requireNotNull(jar.getJarEntry(PROVIDER_RESOURCE)) {
            "No generated gateway projection index was found in ${applicationJar.fileName}; " +
                "apply ifx.subsystem.ksp to the module that declares the projections"
        }
        jar.getInputStream(entry).bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .toList()
        }
    }

private fun GatewayProjection.address(): String = version?.let { "$name/v$it" } ?: name

private const val PROVIDER_RESOURCE =
    "META-INF/services/ifx.gateway.contract.GatewayProjectionProvider"
