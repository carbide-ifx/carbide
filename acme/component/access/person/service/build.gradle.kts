plugins {
    id("component")
}

dependencies{
    implementation(project(":component:access:person:contract"))
    implementation("sonat.ifx:service")
}
