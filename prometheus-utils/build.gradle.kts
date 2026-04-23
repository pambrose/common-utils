description = "Prometheus metrics and monitoring utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.prometheus.core)
    api(libs.prometheus.hotspot)
}
