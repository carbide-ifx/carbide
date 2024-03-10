import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("application")
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}
repositories {
    mavenCentral()
}

dependencies {
    val kotlin = "1.9.22"
    val coroutines = "1.8.0-RC2"
    val kotlinSerialization = "1.6.0"
    val kotlinDatetime = "0.3.2"
    val kotlinLogging = "6.0.3"
    val grpc = "1.57.2"
    val grpcKotlin = "1.4.1"
    runtimeOnly("io.grpc:grpc-netty:$grpc")
//    runtimeOnly("io.grpc:grpc-core:$grpc")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerialization")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinDatetime")
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLogging")


    // TODO: Logging module
    val slf4j = "2.0.3"
    implementation("org.slf4j:slf4j-simple:$slf4j")


    val kotest = "5.8.0"
    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.kotest:kotest-property:$kotest")

}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = "ifx"
            packagePrefix["src/test/kotlin"] = "ifx"
        }
    }
}
