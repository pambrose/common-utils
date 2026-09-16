description = "Google Guava extension utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.guava)

    testImplementation(libs.mockk)
    // ListAppender, to assert the level BooleanMonitor's log actions use.
    testImplementation(libs.logback.classic)
}
