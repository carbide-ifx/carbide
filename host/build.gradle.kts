description = "Service Host"
plugins {
    id("kotlin-library")
}

dependencies {
    val grpc = "1.57.2"
    val grpcKotlin = "1.4.1"
    implementation(project(":proxy"))
    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":context"))
    implementation(project(":logging"))
    runtimeOnly("io.grpc:grpc-netty:$grpc")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

}
