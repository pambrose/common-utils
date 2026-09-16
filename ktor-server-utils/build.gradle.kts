plugins {
    `java-library`
}

description = "Ktor server framework extension utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.ktor.server.core)

    compileOnlyApi(libs.jakarta.servlet.api)

    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
}
