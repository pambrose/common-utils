description = "zipkin-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.brave)
    implementation(libs.zipkin.core)
    implementation(libs.zipkin.reporter)
}
