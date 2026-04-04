description = "Common utilities shared across scripting modules"

dependencies {
    implementation(project(":core-utils"))

    testImplementation(libs.kotest)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
