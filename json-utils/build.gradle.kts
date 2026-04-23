plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "JSON serialization and deserialization utilities"

dependencies {
    implementation(project(":core-utils"))

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
