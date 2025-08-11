description = "core-utils"

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
