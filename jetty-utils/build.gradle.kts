description = "Embedded Jetty server configuration utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.jetty.servlet)

    testImplementation(libs.mockk)
}
