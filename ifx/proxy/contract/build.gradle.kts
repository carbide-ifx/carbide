description = "Service Client Proxy"

plugins {
    id("ifx")
}

dependencies {
    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":protocol:contract"))
    implementation(project(":context"))
}
