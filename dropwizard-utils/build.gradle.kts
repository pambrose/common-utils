description = "dropwizard-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(platform(libs.dropwizard.bom))
    implementation(libs.dropwizard.core)
    implementation(libs.dropwizard.healthcheck)
}
