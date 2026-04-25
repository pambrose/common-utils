import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.pambrose.stable.versions) apply false
    alias(libs.plugins.pambrose.kotlinter) apply false
    alias(libs.plugins.pambrose.testing) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish) apply false
}

version = findProperty("overrideVersion")?.toString() ?: "2.8.1"
group = "com.pambrose.common-utils"

dependencies {
    subprojects.forEach { dokka(it) }
}

dokka {
    moduleName.set("common-utils")
    pluginsConfiguration.html {
        homepageLink.set("https://github.com/pambrose/common-utils")
        footerMessage.set("common-utils")
    }
}

val subprojectPluginIds = listOf(
    libs.plugins.kotlin.jvm,
    libs.plugins.pambrose.kotlinter,
    libs.plugins.pambrose.testing,
    libs.plugins.pambrose.stable.versions,
    libs.plugins.dokka,
    libs.plugins.maven.publish,
).map { it.get().pluginId }

subprojects {
    version = rootProject.version
    group = rootProject.group

    subprojectPluginIds.forEach(pluginManager::apply)

    configureKotlin()
    configurePublishing()
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

fun Project.configurePublishing() {
    extensions.configure<MavenPublishBaseExtension> {
        configure(
            KotlinJvm(
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
