plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
}

description = "Core Kotlin/Java utilities: string, file, collection, and reflection helpers"

kotlin {
    // Targets, toolchain, opt-ins, and compiler flags are configured in the root build.gradle.kts.
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines)
            api(libs.kotlinx.datetime)
        }
        // Only the JVM code logs and uses serialization. kotlin-logging stays `api` because the JVM-only modules
        // get it from here; serialization appears in no public signature.
        jvmMain.dependencies {
            api(libs.kotlin.reflect)
            api(libs.kotlin.logging)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.framework.engine)
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            runtimeOnly(libs.logback.classic)
        }
    }
}
