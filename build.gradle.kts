import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `java-library`
    `maven-publish`

    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply true
    alias(libs.plugins.versions) apply false
    // id("org.jetbrains.kotlinx.kover") version "0.5.0"
}

val versionStr: String by extra
val kotlinLib = libs.plugins.kotlin.jvm.get().toString().split(":").first()
val serializationLib = libs.plugins.kotlin.serialization.get().toString().split(":").first()
val ktlinterLib = libs.plugins.kotlinter.get().toString().split(":").first()
val versionsLib = libs.plugins.versions.get().toString().split(":").first()

allprojects {
    extra["versionStr"] = "2.5.3"
    group = "com.github.pambrose.common-utils"
    version = versionStr

    repositories {
        google()
        mavenCentral()
    }

    //cobertura.coverageSourceDirs = sourceSets.main.groovy.srcDirs
}

subprojects {
    // Suppress Gradle Module Metadata — BOMs are a Maven concept and the .module file
    // causes JitPack to misidentify this as the root project (com.github.pambrose:common-utils)
    // instead of the submodule (com.github.pambrose.common-utils:common-utils-bom)
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }

    if (name != "common-utils-bom") {
        configureKotlin()
        configureVersions()
        configurePublishing()
        configureTesting()
        configureKotlinter()
    }

    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(version)
        return !isStable
    }

    tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }
}

fun Project.configureKotlin() {
    apply {
        plugin(kotlinLib)
    }

    kotlin {
        jvmToolchain(17)

        sourceSets.all {
            listOf(
                "kotlin.contracts.ExperimentalContracts",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlin.time.ExperimentalTime",
                "kotlin.concurrent.atomics.ExperimentalAtomicApi",
                "kotlinx.serialization.ExperimentalSerializationApi",
            ).forEach {
                languageSettings.optIn(it)
            }
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

fun Project.configureVersions() {
    apply {
        plugin(versionsLib)
    }
}

fun Project.configurePublishing() {
    apply {
        plugin("java-library")
        plugin("maven-publish")
    }

    java {
        withSourcesJar()
    }

    publishing {
        val versionStr: String by extra
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = group.toString()
                artifactId = project.name
                version = versionStr
            }
//            create<MavenPublication>("mavenJava") {
//                from(components["java"])
//                artifact(tasks["sourcesJar"])
//                groupId = group.toString()
//                artifactId = project.name
//                version = versionStr
//            }
        }
        repositories {
            maven {
                url = uri("https://jitpack.io")
            }
        }
    }
}

fun Project.configureKotlinter() {
    apply {
        plugin(ktlinterLib)
    }

    kotlinter {
        ignoreFormatFailures = false
        ignoreLintFailures = false
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
