description = "test"
plugins {
    id("conventions-jvm")
}
dependencies {
    val kotest = "5.8.0"

    implementation(project(":service"))

    api("io.kotest:kotest-runner-junit5:$kotest")
    api("io.kotest:kotest-assertions-core:$kotest")
    api("io.kotest:kotest-property:$kotest")
    api("io.kotest:kotest-framework-datatest:$kotest")
    api("io.kotest:kotest-extensions-htmlreporter:$kotest")
    api("io.kotest:kotest-extensions-junitxml:$kotest")
}
