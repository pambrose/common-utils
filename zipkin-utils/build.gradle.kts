description = "Zipkin distributed tracing utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.brave)
    api(libs.zipkin.core)
    api(libs.zipkin.reporter)
}
