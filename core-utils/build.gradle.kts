description = "core-utils"

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)
}
