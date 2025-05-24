description = "Service Client Proxy"

plugins {
    id("conventions-jvm")
}

dependencies {
    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":protocol:rsocket"))
    implementation(project(":context"))
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-client:0.5.1")
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.5.1")
    api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json:0.5.1")
    implementation("io.rsocket.kotlin:ktor-client-rsocket:0.20.0")


    val ktorVersion = "3.0.1"
    // Ktor API
    api("io.ktor:ktor-client-cio-jvm:$ktorVersion")


}
