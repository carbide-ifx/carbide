import gradle.kotlin.dsl.accessors._0b13d6eeaac2650b939609dcb8512350.jar
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings

plugins {
    id("conventions-jvm")
}

idea {
    module {
        settings {
            packagePrefix["main"] = project.packagePath()
            packagePrefix["test"] = project.packagePath()
        }
    }
}

fun Project.packagePath() = group.toString().replace(".component", "").toPackage() + "." + name.toPackage()
fun String.toPackage() = filter { it.isLetterOrDigit() || it == '.' }
tasks.jar {
    // Avoid jar collision when multiple projects have same name
    archiveBaseName = "${project.parent}-${project.name}"
}
