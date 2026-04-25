description = "Common utilities shared across scripting modules"

dependencies {
    api(project(":core-utils"))

    testImplementation(libs.mockk)
}
