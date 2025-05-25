description = "Entire subsystem in single process"

plugins {
    id("subsystem")
    application
}


dependencies {
    implementation(project(":component:access:person:contract"))
    implementation(project(":component:access:person:service"))
    api(project(":component:manager:membership:contract"))
    implementation(project(":component:manager:membership:service"))
    implementation("sonat.ifx:host")
//    implementation("sonat.ifx:proxy-utility")
}
