description = "Entire subsystem in single process"

plugins {
    id("subsystem")
    application
}


dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-client:0.5.1")
    implementation(project(":component:access:person:contract"))
    implementation(project(":component:access:person:service"))
    api(project(":component:manager:membership:contract"))
    implementation(project(":component:manager:membership:service"))
    implementation("ifx:host")
    api("ifx:proxy")
}
