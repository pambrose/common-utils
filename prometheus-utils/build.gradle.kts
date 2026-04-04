description = "Prometheus metrics and monitoring utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.prometheus.core)
    implementation(libs.prometheus.hotspot)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
