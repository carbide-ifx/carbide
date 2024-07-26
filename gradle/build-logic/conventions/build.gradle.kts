description = "Build logic for the project"
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    val kotlinVersion = "2.0.21"
    val kotlinRpcVersion = "0.4.0"
    val jibVersion = "3.4.3"
    val ideaExtVersion = "1.1.7"
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-gradle-plugin:$kotlinRpcVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.google.cloud.tools:jib-gradle-plugin:$jibVersion")
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:$ideaExtVersion")
}
