import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.pambrose.stable.versions) apply false
    alias(libs.plugins.pambrose.kotlinter) apply false
    alias(libs.plugins.pambrose.testing) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish) apply false
}

// version/group are set in gradle.properties; -PoverrideVersion overrides version
allprojects {
    providers.gradleProperty("overrideVersion").orNull?.let { version = it }
}

val scmHost = "github.com/pambrose/common-utils"
val projectHomepage = "https://$scmHost"
val dokkaFooter = "common-utils"

fun DokkaExtension.configureHtml() {
    pluginsConfiguration.html {
        homepageLink.set(projectHomepage)
        footerMessage.set(dokkaFooter)
    }
}

dokka {
    moduleName.set("common-utils")
    configureHtml()
}

val koverExcludeClasses = listOf(
    "com.pambrose.common.concurrent.ConditionalValueKt*",
    "com.pambrose.common.concurrent.LameBooleanWaiterKt*",
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
    libs.plugins.pambrose.stable.versions,
    libs.plugins.dokka,
    libs.plugins.kover,
    libs.plugins.maven.publish,
).map { it.get().pluginId }

subprojects {
    subprojectPluginIds.forEach(pluginManager::apply)

    configureKotlin()
    configureDokka()
    configureKover()
    configurePublishing()

    rootProject.dependencies.add("dokka", this)
    rootProject.dependencies.add("kover", this)
}

fun Project.configureKotlin() {
    extensions.configure<KotlinJvmProjectExtension> {
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
                    // Demo `main()`/`mainN()` functions colocated in production sources for manual playground use.
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
