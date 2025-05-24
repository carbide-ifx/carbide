description = "Base Service"


plugins {
    id("conventions-jvm")
}

dependencies {
    api(project(":context"))
    api("org.jetbrains.kotlinx:kotlinx-rpc-core:0.5.1")
}
