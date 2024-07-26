description = "test"
plugins {
    `java-library`
}
dependencies {
    project(":context")
    project(":host")
    project(":logging")
    project(":naming")
    project(":proxy")
    project(":service")
    project(":stdlib")
    project(":test")
}
