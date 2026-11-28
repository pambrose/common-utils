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
            // JsonElement and the kotlinx.serialization types appear throughout the public API.
            api(libs.kotlinx.serialization.json)
            // KLogger is in the public API through the deprecated JsonElementUtils.logger. As `implementation`, a
            // consumer's commonMain, JS and wasmJs code could not compile a call to it; only the JVM (through
            // core-utils) and native targets saw the type. Return it to `implementation` when 6.0.0 removes
            // JsonElementUtils.
            api(libs.kotlin.logging)
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
