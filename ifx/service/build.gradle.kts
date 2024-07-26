description = "Base Service"


plugins {
    id("conventions-jvm")
}

dependencies {
    implementation(project(":context"))
    api("org.jetbrains.kotlinx:kotlinx-rpc-core:0.4.0")
}
