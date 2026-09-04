package ifx.build.jib

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.LogEvent
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import com.google.cloud.tools.jib.api.buildplan.Port
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

@TaskAction
fun buildTar(
    @Input runtimeClasspath: Classpath,
    @Input applicationJar: CompilationArtifact,
    mainClass: String?,
    @Input settings: JibSettings,
    @Output outputTar: Path,
) {
    val target = TarImage.at(outputTar).named(settings.image)
    containerize(runtimeClasspath, applicationJar, mainClass, settings, Containerizer.to(target))
}

@TaskAction
fun buildToDockerDaemon(
    @Input runtimeClasspath: Classpath,
    @Input applicationJar: CompilationArtifact,
    mainClass: String?,
    @Input settings: JibSettings,
) {
    val target = DockerDaemonImage.named(ImageReference.parse(settings.image))
    containerize(runtimeClasspath, applicationJar, mainClass, settings, Containerizer.to(target))
}

@TaskAction
fun pushToRegistry(
    @Input runtimeClasspath: Classpath,
    @Input applicationJar: CompilationArtifact,
    mainClass: String?,
    @Input settings: JibSettings,
) {
    val target = registryImage(settings.image, settings.targetCredentialHelper)
    containerize(runtimeClasspath, applicationJar, mainClass, settings, Containerizer.to(target))
}

private fun containerize(
    runtimeClasspath: Classpath,
    applicationJar: CompilationArtifact,
    mainClass: String?,
    settings: JibSettings,
    containerizer: Containerizer,
) {
    settings.tags.forEach(containerizer::withAdditionalTag)
    containerizer
        .setToolName("ifx-jib")
        .addEventHandler(LogEvent::class.java) { event ->
            println("${event.level}: ${event.message}")
        }

    jibContainerBuilder(runtimeClasspath, applicationJar, mainClass, settings).containerize(containerizer)
}

private fun jibContainerBuilder(
    runtimeClasspath: Classpath,
    applicationJar: CompilationArtifact,
    mainClass: String?,
    settings: JibSettings,
): JibContainerBuilder {
    val applicationJarPath = applicationJar.artifact
    val dependencies = runtimeClasspath.resolvedFiles.filterNot { it == applicationJarPath }
    val configuredMainClass = requireNotNull(mainClass) {
        "The containerized module must set settings.jvm.mainClass in module.yaml"
    }

    return JavaContainerBuilder
        .from(registryImage(settings.baseImage, settings.baseCredentialHelper))
        .addDependencies(dependencies)
        .addProjectDependencies(applicationJarPath)
        .addJvmFlags(settings.jvmArgs)
        .setMainClass(configuredMainClass)
        .toContainerBuilder()
        .apply {
            setEnvironment(settings.environment)
            setLabels(settings.labels)
            setExposedPorts(settings.ports.mapTo(linkedSetOf(), Port::tcp))
            extraDirectoriesLayer(settings.extraDirectories)?.let(::addFileEntriesLayer)
        }
}

internal fun extraDirectoriesLayer(directories: List<JibExtraDirectory>): FileEntriesLayer? {
    if (directories.isEmpty()) return null

    return FileEntriesLayer.builder()
        .setName("extra directories")
        .apply {
            directories.forEach { directory ->
                require(directory.source.toFile().isDirectory) {
                    "Extra directory does not exist: ${directory.source.toAbsolutePath()}"
                }
                val destination = try {
                    AbsoluteUnixPath.get(directory.destination)
                } catch (exception: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Extra directory destination must be an absolute Unix path: ${directory.destination}",
                        exception,
                    )
                }
                addEntryRecursive(directory.source, destination)
            }
        }
        .build()
}

private fun registryImage(name: String, credentialHelper: String?): RegistryImage {
    val reference = ImageReference.parse(name)
    val image = RegistryImage.named(reference)
    val retrievers = CredentialRetrieverFactory.forImage(reference) { event ->
        println("${event.level}: ${event.message}")
    }
    image.addCredentialRetriever(retrievers.dockerConfig())
    image.addCredentialRetriever(retrievers.wellKnownCredentialHelpers())
    if (credentialHelper != null) {
        image.addCredentialRetriever(retrievers.dockerCredentialHelper(credentialHelper))
    }
    return image
}
