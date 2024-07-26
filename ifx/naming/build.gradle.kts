description = "Code policies"
plugins {
    id("conventions-jvm")
}
dependencies {
    val konsistVersion = "0.15.1"
    val kotestVersion = "5.8.1"
    testImplementation("com.lemonappdev:konsist:$konsistVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")

    testImplementation(project(":service"))
}


