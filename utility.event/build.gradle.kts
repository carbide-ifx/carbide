plugins {
    id("njord.kotlin-library")
}

dependencies {
    implementation(platform("com.azure:azure-sdk-bom:1.2.38"))
    implementation("com.azure:azure-messaging-servicebus")
    implementation("com.azure:azure-identity")
    implementation("com.azure.resourcemanager:azure-resourcemanager-servicebus:2.53.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation(project(":utility:context"))
}
