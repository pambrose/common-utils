import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ben.manes.versions) apply false
    alias(libs.plugins.pambrose.kotlinter) apply false
    alias(libs.plugins.pambrose.testing) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish) apply false
}

// version/group are set in gradle.properties; -PoverrideVersion overrides version
allprojects {
    providers.gradleProperty("overrideVersion").orNull?.let { version = it }
}

val projectName = "common-utils"
val scmHost = "github.com/pambrose/$projectName"
val projectHomepage = "https://$scmHost"
val jvmTargetVersion = libs.versions.jvmTarget.get()
val detektConfigDir = "config/detekt"

fun DokkaExtension.configureHtml() {
    pluginsConfiguration.html {
        homepageLink.set(projectHomepage)
        footerMessage.set(projectName)
    }
}

dokka {
    moduleName.set(projectName)
    configureHtml()
}

// Demo `main()`/`mainN()` functions colocated in production sources for manual playground use.
val koverExcludeClasses = listOf(
    "com.pambrose.common.concurrent.ConditionalValueKt*",
    "com.pambrose.common.concurrent.GenericValueWaiterKt*",
)

kover {
    reports {
        filters {
            excludes {
                classes(koverExcludeClasses)
            }
        }
    }
}

val subprojectPluginIds = listOf(
    libs.plugins.kotlin.jvm,
    libs.plugins.pambrose.kotlinter,
    libs.plugins.pambrose.testing,
    libs.plugins.ben.manes.versions,
    libs.plugins.detekt,
    libs.plugins.dokka,
    libs.plugins.kover,
    libs.plugins.maven.publish,
).map { it.get().pluginId }

subprojects {
    subprojectPluginIds.forEach(pluginManager::apply)

    configureKotlin()
    configureDetekt()
    configureDokka()
    configureKover()
    configurePublishing()

    rootProject.dependencies.add("dokka", this)
    rootProject.dependencies.add("kover", this)
}

fun Project.configureKotlin() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(jvmTargetVersion.toInt())

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

        // Run the unused-return-value checker over production code only. Kotest's
        // assertion DSL (e.g. shouldBe) returns its receiver, and tests intentionally
        // discard that result, so applying the checker to the test source set would
        // emit only false-positive warnings.
        tasks.named<KotlinCompile>("compileKotlin") {
            compilerOptions {
                freeCompilerArgs.add("-Xreturn-value-checker=check")
            }
        }
    }
}

fun Project.configureDetekt() {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        autoCorrect.set(false)
        parallel.set(true)
        basePath.set(rootProject.layout.projectDirectory)
        val sharedConfig = rootProject.file("$detektConfigDir/detekt.yml")
        if (sharedConfig.exists()) {
            config.setFrom(sharedConfig)
        }
        val sharedBaseline = rootProject.file("$detektConfigDir/baseline.xml")
        if (sharedBaseline.exists()) {
            baseline.set(sharedBaseline)
        }
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set(jvmTargetVersion)
        reports {
            html.required.set(true)
            checkstyle.required.set(true)
            sarif.required.set(false)
            markdown.required.set(false)
        }
    }
    tasks.named("detekt").configure { dependsOn("detektMain", "detektTest") }
    tasks.named("detektBaseline").configure { dependsOn("detektBaselineMain", "detektBaselineTest") }
}

fun Project.configureDokka() {
    extensions.configure<DokkaExtension> {
        configureHtml()
    }
}

fun Project.configureKover() {
    extensions.configure<KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    classes(koverExcludeClasses)
                }
            }
        }
    }
}

fun Project.configurePublishing() {
    extensions.configure<MavenPublishBaseExtension> {
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = SourcesJar.Sources(),
            ),
        )
        coordinates(project.group.toString(), project.name, project.version.toString())

        pom {
            name.set(provider { project.name })
            description.set(provider { project.description })
            url.set(projectHomepage)
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
                connection.set("scm:git:https://$scmHost.git")
                developerConnection.set("scm:git:ssh://$scmHost.git")
                url.set(projectHomepage)
            }
        }

        publishToMavenCentral(automaticRelease = true)
        if (providers.gradleProperty("signingInMemoryKey").isPresent) {
            signAllPublications()
        }
    }
}
