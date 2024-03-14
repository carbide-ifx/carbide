description = "Base Service"
plugins {
    id("kotlin-library")
}

dependencies {
    implementation(project(":context"))
    val grpc = "1.57.2"
    val grpcKotlin = "1.4.1"
    runtimeOnly("io.grpc:grpc-netty:$grpc")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlin")

}
