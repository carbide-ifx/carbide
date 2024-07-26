description = "Service Client Proxy"
val kotlinRpcVersion = "0.2.4"

plugins {
    id("conventions-jvm")
}

dependencies {
    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":context"))
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-client:0.4.0")
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.4.0")
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json:0.4.0")

    val ktorVersion = "3.0.1"
    // Ktor API
    api("io.ktor:ktor-client-cio-jvm:$ktorVersion")
}
