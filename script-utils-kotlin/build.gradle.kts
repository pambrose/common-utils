description = "Kotlin scripting engine utilities"

dependencies {
    // script-utils-common already exports core-utils as `api`.
    api(project(":script-utils-common"))

    runtimeOnly(libs.kotlin.scripting)
}

// These tests run the Kotlin compiler in-process (the JSR-223 engine compiles every snippet), so the
// worker needs far more heap than Gradle's 512m default. Exceeding it surfaces as a misleading
// "Could not read file: ...kotlin-stdlib.jar!/...class" from the compiler rather than as a plain
// OutOfMemoryError, so leave this set even if the suite happens to fit on the current Kotlin release.
tasks.test {
    maxHeapSize = "2g"
}
