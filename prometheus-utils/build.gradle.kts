description = "Prometheus metrics and monitoring utilities"

dependencies {
    implementation(libs.kotlin.logging)

    api(libs.prometheus.metrics.core)
    api(libs.prometheus.metrics.instrumentation.jvm)

    testImplementation(libs.mockk)
    testImplementation(libs.prometheus.metrics.exposition.textformats)
}
