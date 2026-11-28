description = "Prometheus metrics and monitoring utilities"

dependencies {
    implementation(libs.kotlin.logging)

    api(libs.prometheus.metrics.core)
    // SystemMetrics registers its JVM and process collectors internally; none of its types is in the public API.
    implementation(libs.prometheus.metrics.instrumentation.jvm)

    testImplementation(libs.mockk)
    testImplementation(libs.prometheus.metrics.exposition.textformats)
}
