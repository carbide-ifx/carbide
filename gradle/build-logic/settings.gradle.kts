rootProject.name = "build-logic"
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal() // so that external plugins can be resolved in dependencies section
    }
}
include("conventions")
