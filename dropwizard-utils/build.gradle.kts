description = "Dropwizard framework integration utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.dropwizard.core)
    implementation(libs.dropwizard.healthcheck)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
