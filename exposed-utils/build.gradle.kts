description = "Jetbrains Exposed ORM extension utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.jodatime)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
