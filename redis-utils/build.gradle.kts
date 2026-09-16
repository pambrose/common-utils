description = "Redis client extension utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.redis)

    testImplementation(libs.logback.classic)
    testImplementation(libs.mockk)
}
