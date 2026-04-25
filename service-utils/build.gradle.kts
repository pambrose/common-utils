description = "Service lifecycle and management utilities"

dependencies {
    api(project(":core-utils"))
    implementation(project(":ktor-server-utils"))
    implementation(project(":guava-utils"))
    implementation(project(":jetty-utils"))
    implementation(project(":prometheus-utils"))
    implementation(project(":dropwizard-utils"))
    implementation(project(":zipkin-utils"))

    implementation(libs.bundles.dropwizard.service)
    implementation(libs.bundles.ktor.server.service)
    implementation(libs.bundles.prometheus.service)
    implementation(libs.zipkin.sender.okhttp)

    testImplementation(libs.mockk)
}
