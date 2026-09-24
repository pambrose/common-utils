description = "Jetbrains Exposed ORM extension utilities"

dependencies {
    // KotlinSqlLogger takes a KLogger.
    api(libs.kotlin.logging)

    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.jodatime)

    testImplementation(libs.h2)
    testImplementation(libs.mockk)
}
