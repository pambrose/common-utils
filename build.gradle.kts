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

allprojects {
    version = findProperty("overrideVersion")?.toString() ?: "2.8.2"
    group = "com.pambrose.common-utils"
}

val projectHomepage = "https://github.com/pambrose/common-utils"
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

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.pambrose.common.concurrent.ConditionalValueKt*",
                    "com.pambrose.common.concurrent.LameBooleanWaiterKt*",
                    "com.pambrose.common.concurrent.GenericValueWaiterKt*",
                )
            }
        }
    }
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
                    classes(
                        "com.pambrose.common.concurrent.ConditionalValueKt*",
                        "com.pambrose.common.concurrent.LameBooleanWaiterKt*",
                        "com.pambrose.common.concurrent.GenericValueWaiterKt*",
                    )
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
        coordinates(group.toString(), project.name, version.toString())

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
        if (project.findProperty("signingInMemoryKey") != null) {
            signAllPublications()
        }
    }
}
