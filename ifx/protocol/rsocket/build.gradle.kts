description = "protocol.rsocket"
plugins {
    id("conventions-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    val ktor_version = "3.1.1"
    api(project(":protocol:contract"))
    api(project(":service"))
    api(project(":naming"))
    api("io.rsocket.kotlin:ktor-server-rsocket:0.20.0")

    testImplementation(project(":proxy"))


    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-websockets:$ktor_version")


    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
}

