plugins {
    id("component")
}

dependencies {
    implementation(project(":component:manager:membership:contract"))
    implementation(project(":component:access:person:contract"))

    implementation("sonat.ifx:service")
    implementation("sonat.ifx:proxy")
}
