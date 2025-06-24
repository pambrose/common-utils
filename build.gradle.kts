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

allprojects {
    extra["versionStr"] = "2.3.11"
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
}

fun Project.configurePublishing() {
    apply {
        plugin("java-library")
        plugin("maven-publish")
    }

//    val versionStr: String by extra
//
//    publishing {
//        publications {
//            create<MavenPublication>("maven") {
//                groupId = group.toString()
//                artifactId = project.name
//                version = versionStr
//                from(components["java"])
//
//                // Add sources jar to publication
//                artifact(tasks["sourcesJar"])
//            }
//        }
//    }
    // This is to fix a bizarre gradle error
    tasks.named<Jar>("jar") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    java {
        withSourcesJar()
    }
}

val kotlinLib = libs.plugins.kotlin.jvm.get().toString().split(":").first()
val serializationLib = libs.plugins.kotlin.serialization.get().toString().split(":").first()
val ktlinterLib = libs.plugins.kotlinter.get().toString().split(":").first()

subprojects {
    configureKotlin()
    configurePublishing()

    apply {
        plugin(kotlinLib)
        plugin(serializationLib)
        plugin(ktlinterLib)
    }

    // This is to fix a bizarre gradle error
//    tasks.named<Jar>("jar") {
//        duplicatesStrategy = DuplicatesStrategy.INCLUDE
//    }

//    tasks.register<Jar>("sourcesJar") {
//        dependsOn("classes")
//        from(sourceSets["main"].allSource)
//        archiveClassifier.set("sources")
//    }
//
//    tasks.register<Jar>("javadocJar") {
//        dependsOn("javadoc")
//        from(tasks.named<Javadoc>("javadoc").get().destinationDir)
//        archiveClassifier.set("javadoc")
//    }

//    artifacts {
//        add("archives", tasks.named("sourcesJar"))
//        // add("archives", tasks.named("javadocJar"))
//    }

//    java {
//        withSourcesJar()
//    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.contracts.ExperimentalContracts",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlin.time.ExperimentalTime",
                    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi"
                )
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }

    configure<KotlinterExtension> {
        reporters = arrayOf("checkstyle", "plain")
    }
}
