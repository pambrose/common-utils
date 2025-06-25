plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "json-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlinx.serialization)

    testImplementation(kotlin("test"))
}
