description = "dropwizard-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.dropwizard.core)
    implementation(libs.dropwizard.healthcheck)
}
