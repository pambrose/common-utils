description = "exposed-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jodatime)
}
