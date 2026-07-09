plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
            api(libs.kotlinx.serialization.json)
            api(libs.kotlin.logging)
        }
        jvmMain.dependencies {
            api(libs.kotlin.reflect)
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
