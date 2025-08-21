description = "core-utils"

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    implementation(libs.kotlin.bom)
    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
