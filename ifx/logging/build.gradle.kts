description = "Logging utilities"

plugins {
    id("conventions-jvm")
}

dependencies {
    val log4j = "2.23.1"
    implementation("org.apache.logging.log4j:log4j-api:$log4j")
    implementation("org.apache.logging.log4j:log4j-core:$log4j")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j")
    implementation("org.apache.logging.log4j:log4j-layout-template-json:$log4j")
    implementation("org.slf4j:jul-to-slf4j:2.0.10")
}
