import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jmailen.gradle.kotlinter.KotlinterExtension

plugins {
    `java-library`
    `maven-publish`

    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply true
    alias(libs.plugins.versions) apply true

    // id("org.jetbrains.kotlinx.kover") version "0.5.0"
}

val versionStr: String by extra
val kotlinLib = libs.plugins.kotlin.jvm.get().toString().split(":").first()
val serializationLib = libs.plugins.kotlin.serialization.get().toString().split(":").first()
val ktlinterLib = libs.plugins.kotlinter.get().toString().split(":").first()

allprojects {
    extra["versionStr"] = "2.4.0"
    group = "com.github.pambrose.common-utils"
    version = versionStr

    repositories {
        google()
        mavenCentral()
    }

    //cobertura.coverageSourceDirs = sourceSets.main.groovy.srcDirs
}

fun Project.configureKotlin() {
    apply {
        plugin(kotlinLib)
    }

    kotlin {
        jvmToolchain(11)
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.contracts.ExperimentalContracts",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlin.time.ExperimentalTime",
                    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
                    "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                )
            )
        }
    }
}

fun Project.configurePublishing() {
    apply {
        plugin("java-library")
        plugin("maven-publish")
    }

    publishing {
        val versionStr: String by extra
        publications {
            create<MavenPublication>("maven") {
                groupId = group.toString()
                artifactId = project.name
                version = versionStr
                from(components["java"])
            }
        }
    }

    java {
        withSourcesJar()
    }
}

fun Project.configureKotlinter() {
    apply {
        plugin(ktlinterLib)
    }

    configure<KotlinterExtension> {
        reporters = arrayOf("checkstyle", "plain")
    }
}

fun Project.configureTesting() {
    tasks.test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

subprojects {
    configureKotlin()
    configurePublishing()
    configureTesting()
    configureKotlinter()
}
