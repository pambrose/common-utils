description = "Dropwizard framework integration utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.dropwizard.core)
    api(libs.dropwizard.healthcheck)
}
