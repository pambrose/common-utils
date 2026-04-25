description = "Prometheus metrics and monitoring utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.prometheus.core)
    api(libs.prometheus.hotspot)
}
