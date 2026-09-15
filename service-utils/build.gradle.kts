description = "Service lifecycle and management utilities"

dependencies {
    api(project(":core-utils"))
    // Types from these appear in the public API (supertypes, public properties, and constructor and
    // function parameters), so consumers need them on their compile classpath.
    api(project(":ktor-server-utils"))
    api(project(":guava-utils"))
    api(project(":jetty-utils"))
    api(project(":dropwizard-utils"))
    api(project(":zipkin-utils"))
    api(libs.dropwizard.jmx)

    implementation(project(":prometheus-utils"))

    implementation(libs.bundles.dropwizard.service)
    implementation(libs.bundles.ktor.server.service)
    implementation(libs.bundles.prometheus.service)
    implementation(libs.zipkin.sender.okhttp)

    testImplementation(libs.mockk)
    // ListAppender, to assert the level service failures are logged at.
    testImplementation(libs.logback.classic)
}
