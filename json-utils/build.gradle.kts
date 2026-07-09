plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
}

description = "JSON serialization and deserialization utilities"

kotlin {
    // Targets, toolchain, opt-ins, and compiler flags are configured in the root build.gradle.kts.
    sourceSets {
        commonMain.dependencies {
            api(project(":core-utils"))
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
