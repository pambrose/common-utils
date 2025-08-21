description = "service-utils"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":guava-utils"))
    implementation(project(":jetty-utils"))
    implementation(project(":prometheus-utils"))
    implementation(project(":dropwizard-utils"))
    implementation(project(":zipkin-utils"))

    implementation(libs.guava)

    implementation(platform(libs.prometheus.bom))
    implementation(libs.prometheus.servlet)
    implementation(libs.prometheus.dropwizard)

    implementation(platform(libs.dropwizard.bom))
    implementation(libs.dropwizard.core)
    implementation(libs.dropwizard.healthcheck)
    implementation(libs.dropwizard.servlets)
    implementation(libs.dropwizard.jmx)

    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.brave)
    implementation(libs.zipkin.core)
    implementation(libs.zipkin.reporter)
    implementation(libs.zipkin.sender.okhttp)
}
