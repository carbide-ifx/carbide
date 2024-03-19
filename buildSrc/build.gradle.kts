plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    val kotlinVersion = "1.9.22"
    val jibVersion = "3.3.2"
    val ideaExtVersion = "1.1.7"
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.google.cloud.tools:jib-gradle-plugin:$jibVersion")
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:$ideaExtVersion")
}
