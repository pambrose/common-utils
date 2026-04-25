plugins {
    `java-library`
}

description = "Ktor server framework extension utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)
    api(libs.ktor.server.core)

    compileOnlyApi(libs.jakarta.servlet.api)

    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.ktor.server.test.host)
}
