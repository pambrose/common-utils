import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
    extra["versionStr"] = "2.4.3"
    group = "com.github.pambrose.common-utils"
    version = versionStr

    repositories {
        google()
        mavenCentral()
    }

    //cobertura.coverageSourceDirs = sourceSets.main.groovy.srcDirs
}

subprojects {
    if (name != "common-utils-bom") {
        configureKotlin()
        configurePublishing()
        configureTesting()
        configureKotlinter()
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
