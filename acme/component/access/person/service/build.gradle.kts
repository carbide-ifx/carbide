plugins {
    id("component")
}

dependencies{
    implementation(project(":component:access:person:contract"))
    implementation("ifx:service")
}
