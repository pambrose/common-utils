description = "Prometheus metrics and monitoring utilities"

dependencies {
    implementation(libs.kotlin.logging)

    api(libs.prometheus.core)
    api(libs.prometheus.hotspot)

    testImplementation(libs.mockk)
}
