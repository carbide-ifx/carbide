description = "Service Host"
plugins {
    id("conventions-jvm")
}

dependencies {
    val ktorVersion = "3.0.1"
    val kodeinVersion = "7.22"
    implementation(project(":proxy"))
    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":context"))
    implementation(project(":logging"))
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-server:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-server:0.4.0")

    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-cbor:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-protobuf:0.4.0")

}

