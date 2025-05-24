description = "Service Host"
plugins {
    id("conventions-jvm")
}

repositories {
//    mavenLocal()
    mavenCentral()
}

dependencies {

    implementation(project(":naming"))
    implementation(project(":service"))
    implementation(project(":context"))
    implementation(project(":logging"))
    implementation(project(":protocol:contract"))
    implementation(project(":protocol:rsocket"))
}

