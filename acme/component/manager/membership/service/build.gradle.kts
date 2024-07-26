plugins {
    id("component")
}

dependencies {
    implementation(project(":component:manager:membership:contract"))
    implementation(project(":component:access:person:contract"))

    implementation("ifx:service")
    implementation("ifx:proxy")
}
