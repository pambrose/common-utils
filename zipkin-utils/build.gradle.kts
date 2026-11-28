description = "Zipkin distributed tracing utilities"

dependencies {
    // No core-utils type is in the public API, and no code here uses core-utils. It stays on the runtime classpath
    // only so that consumers compiled against an earlier release, which exported it as `api`, keep running; 6.0.0
    // drops it.
    runtimeOnly(project(":core-utils"))

    api(libs.brave)
    api(libs.zipkin.core)
    api(libs.zipkin.reporter)
}
