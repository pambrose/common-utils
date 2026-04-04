import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `java-library`

    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply true
    alias(libs.plugins.versions) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish) apply false
    // id("org.jetbrains.kotlinx.kover") version "0.5.0"
}

dependencies {
    subprojects.forEach { dokka(project(it.path)) }
}

val versionStr: String by extra
val kotlinLib = libs.plugins.kotlin.jvm.get().toString().split(":").first()
val serializationLib = libs.plugins.kotlin.serialization.get().toString().split(":").first()
val ktlinterLib = libs.plugins.kotlinter.get().toString().split(":").first()
val versionsLib = libs.plugins.versions.get().toString().split(":").first()

allprojects {
    extra["versionStr"] = findProperty("overrideVersion")?.toString() ?: "2.7.1"
    group = "com.pambrose.common-utils"
    version = versionStr

    repositories {
        google()
        mavenCentral()
    }

    //cobertura.coverageSourceDirs = sourceSets.main.groovy.srcDirs
}

// Disable publishing for the root project — only subprojects should be published
tasks.withType<PublishToMavenRepository>().configureEach { enabled = false }
tasks.withType<PublishToMavenLocal>().configureEach { enabled = false }

dokka {
    moduleName.set("common-utils")
    pluginsConfiguration.html {
        homepageLink.set("https://github.com/pambrose/common-utils")
        footerMessage.set("common-utils")
    }
}

subprojects {
    configureKotlin()
    configureVersions()
    configurePublishing()
    configureTesting()
    configureKotlinter()

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
        plugin("org.jetbrains.dokka")
        plugin("com.vanniktech.maven.publish")
    }

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        configure(
            com.vanniktech.maven.publish.KotlinJvm(
                javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = SourcesJar.Sources(),
            ),
        )
        coordinates("com.pambrose.common-utils", project.name, version.toString())

        pom {
            name.set(project.name)
            description.set(provider { project.description })
            url.set("https://github.com/pambrose/common-utils")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("pambrose")
                    name.set("Paul Ambrose")
                    email.set("paul@pambrose.com")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/pambrose/common-utils.git")
                developerConnection.set("scm:git:ssh://github.com/pambrose/common-utils.git")
                url.set("https://github.com/pambrose/common-utils")
            }
        }

        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
    }

    // Skip signing when no GPG key is provided (e.g., local publishing)
    tasks.withType<Sign>().configureEach {
        isEnabled = project.findProperty("signingInMemoryKey") != null
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
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }
}
