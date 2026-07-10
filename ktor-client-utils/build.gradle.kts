plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
}

description = "Ktor HTTP client extension utilities"

kotlin {
    // Targets, toolchain, opt-ins, and compiler flags are configured in the root build.gradle.kts.
    sourceSets {
        commonMain.dependencies {
            api(project(":core-utils"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.framework.engine)
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.ktor.client.cio)
            runtimeOnly(libs.logback.classic)
        }
    }
}
