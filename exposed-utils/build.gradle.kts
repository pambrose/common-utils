description = "exposed-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)

    implementation(platform(libs.exposed.bom))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.jodatime)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
