plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "ifx-kotlin"

include(":context")
include(":host")
include(":naming")
include(":logging")
include(":proxy")
include(":service")
include(":transport")
