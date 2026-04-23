description = "Dropwizard framework integration utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.dropwizard.core)
    api(libs.dropwizard.healthcheck)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
