description = "Base Service"
plugins {
    id("kotlin-library")
}

dependencies {
    val grpc = "1.57.2"
    val grpcKotlin = "1.4.1"
    implementation(project(":context"))
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlin")
    runtimeOnly("io.grpc:grpc-netty:$grpc")

}
