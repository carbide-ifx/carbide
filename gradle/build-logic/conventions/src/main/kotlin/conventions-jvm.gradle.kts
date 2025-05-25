import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings

plugins {
    idea
    kotlin("jvm")
    `java-library`
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    val kotlinSerialization = "1.6.0"
    val kotest = "5.8.0"
    val coroutines = "1.8.0-RC2"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerialization")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("org.slf4j:slf4j-api:2.0.7")

    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.kotest:kotest-property:$kotest")
}

idea {
    module {
        settings {
            packagePrefix["main"] = "${rootProject.name}.${project.name}"
            packagePrefix["test"] = "${rootProject.name}.${project.name}"
        }
    }
}

sourceSets {
    main {
        kotlin.setSrcDirs(listOf("main"))
        resources.setSrcDirs(listOf("main/resources"))
        java.setSrcDirs(emptyList<String>())
    }

    test {
        kotlin.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("test/resources"))
        java.setSrcDirs(emptyList<String>())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.jar {
    // Avoid jar collision when multiple projects have same name
    archiveBaseName = project.name + project.parent?.name
}
