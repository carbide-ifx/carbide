import org.gradle.kotlin.dsl.application
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings

plugins {
    id("conventions-jvm")
    application

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
