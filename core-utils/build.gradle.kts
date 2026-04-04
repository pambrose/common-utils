description = "Core Kotlin/Java utilities: string, file, collection, and reflection helpers"

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
