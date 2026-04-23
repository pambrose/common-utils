description = "Service lifecycle and management utilities"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":ktor-server-utils"))
    implementation(project(":guava-utils"))
    implementation(project(":jetty-utils"))
    implementation(project(":prometheus-utils"))
    implementation(project(":dropwizard-utils"))
    implementation(project(":zipkin-utils"))

    implementation(libs.prometheus.servlet)
    implementation(libs.prometheus.dropwizard)

    implementation(libs.dropwizard.servlets)
    implementation(libs.dropwizard.jmx)

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.compression)

    implementation(libs.zipkin.sender.okhttp)
}
