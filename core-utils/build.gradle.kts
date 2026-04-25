plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Core Kotlin/Java utilities: string, file, collection, and reflection helpers"

dependencies {
    api(libs.kotlin.reflect)
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlin.logging)
}
