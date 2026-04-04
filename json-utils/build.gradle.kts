plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "JSON serialization and deserialization utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
