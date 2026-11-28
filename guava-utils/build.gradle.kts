description = "Google Guava extension utilities"

dependencies {
    // No core-utils type is in the public API, and no code here uses core-utils. It stays on the runtime classpath
    // only so that consumers compiled against an earlier release, which exported it as `api`, keep running; 6.0.0
    // drops it.
    implementation(project(":core-utils"))

    api(libs.guava)
    // KLogger is a parameter of the public Service.genericServiceListener.
    api(libs.kotlin.logging)

    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.mockk)
    // ListAppender, to assert the level BooleanMonitor's log actions use.
    testImplementation(libs.logback.classic)
}
