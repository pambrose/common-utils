plugins {
    `java-library`
}

description = "Ktor server framework extension utilities"

dependencies {
    // Declared directly rather than inherited: this module used none of core-utils' own code, but reached
    // kotlin-logging through core-utils' `api` export. Dropping core-utils without this breaks the build.
    implementation(libs.kotlin.logging)

    api(libs.ktor.server.core)

    compileOnlyApi(libs.jakarta.servlet.api)

    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
}
