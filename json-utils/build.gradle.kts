plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "JSON serialization and deserialization utilities"

dependencies {
    api(project(":core-utils"))
}
