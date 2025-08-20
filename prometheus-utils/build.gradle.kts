description = "prometheus-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(platform(libs.prometheus.bom))
    implementation(libs.prometheus.core)
    implementation(libs.prometheus.hotspot)
}
