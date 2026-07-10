import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import io.kotest.framework.gradle.KotestGradleExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.Family
import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ben.manes.versions) apply false
    alias(libs.plugins.pambrose.kotlinter) apply false
    alias(libs.plugins.pambrose.testing) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.kotest) apply false
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

val experimentalOptIns = listOf(
    "kotlin.contracts.ExperimentalContracts",
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlin.time.ExperimentalTime",
    "kotlin.concurrent.atomics.ExperimentalAtomicApi",
    "kotlinx.serialization.ExperimentalSerializationApi",
)

val returnValueCheckerArg = "-Xreturn-value-checker=check"

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

// Modules built with kotlin("multiplatform"); all other modules stay kotlin("jvm").
val kmpModuleNames = setOf(
    "core-utils",
    "json-utils",
    "ktor-client-utils",
)

// The pambrose convention plugins assume the kotlin/jvm plugin, so KMP modules apply
// the raw kotlinter plugin instead and get the equivalent configuration inline below.
val jvmPluginIds = listOf(
    libs.plugins.kotlin.jvm,
    libs.plugins.pambrose.kotlinter,
    libs.plugins.pambrose.testing,
).map { it.get().pluginId }

val kmpPluginIds = listOf(
    libs.plugins.kotlinter,
).map { it.get().pluginId }

val sharedPluginIds = listOf(
    libs.plugins.ben.manes.versions,
    libs.plugins.detekt,
    libs.plugins.dokka,
    libs.plugins.kover,
    libs.plugins.maven.publish,
).map { it.get().pluginId }

subprojects {
    val isKmp = name in kmpModuleNames

    ((if (isKmp) kmpPluginIds else jvmPluginIds) + sharedPluginIds).forEach(pluginManager::apply)

    if (isKmp) {
        // The multiplatform plugin itself is applied by each KMP module's own plugins
        // block; targets and publishing can only be configured once it is present.
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configureKotlinMultiplatform()
            configurePublishing(isKmp = true)
        }
        // The kotest plugin queries customGradleTask without a default in some code
        // paths; false means "wire specs into the standard Gradle test tasks".
        pluginManager.withPlugin("io.kotest") {
            extensions.configure<KotestGradleExtension> {
                customGradleTask.set(false)
            }
        }
        configureKotlinterForKmp()
    } else {
        configureKotlinJvm()
        configurePublishing(isKmp = false)
    }

    configureDetekt()
    configureDokka()
    configureKover()
    configureVersions()

    rootProject.dependencies.add("dokka", this)
    rootProject.dependencies.add("kover", this)
}

fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(jvmTargetVersion.toInt())

        sourceSets.all {
            experimentalOptIns.forEach {
                languageSettings.optIn(it)
            }
        }

        // Run the unused-return-value checker over production code only. Kotest's
        // assertion DSL (e.g. shouldBe) returns its receiver, and tests intentionally
        // discard that result, so applying the checker to the test source set would
        // emit only false-positive warnings.
        tasks.named<KotlinCompile>("compileKotlin") {
            compilerOptions {
                freeCompilerArgs.add(returnValueCheckerArg)
            }
        }
    }
}

fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(jvmTargetVersion.toInt())

        jvm()
        js { nodejs() }
        wasmJs { nodejs() }
        macosArm64()
        iosArm64()
        iosX64()
        iosSimulatorArm64()
        tvosArm64()
        tvosSimulatorArm64()
        watchosArm32()
        watchosArm64()
        watchosSimulatorArm64()
        watchosDeviceArm64()
        linuxX64()
        linuxArm64()
        mingwX64()

        sourceSets.configureEach {
            experimentalOptIns.forEach {
                languageSettings.optIn(it)
            }
        }

        // Same production-only unused-return-value rule as the JVM modules, applied
        // to every target's main compilation (tests discard Kotest assertion results).
        targets.configureEach {
            compilations.matching { it.name == "main" }.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add(returnValueCheckerArg)
                    }
                }
            }
        }

        // Xcode installs the iOS simulator runtime by default, but not the watchOS/tvOS
        // ones; macOS + iOS simulator runs already exercise the Apple targets.
        targets.withType<KotlinNativeTargetWithSimulatorTests>()
            .matching { it.konanTarget.family == Family.WATCHOS || it.konanTarget.family == Family.TVOS }
            .configureEach {
                tasks.named("${name}Test") { enabled = false }
            }
    }

    // The com.pambrose.testing convention plugin does this for the JVM modules.
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Kotest discovers commonTest specs on the non-JVM targets too, but a target whose
    // test binary contains no specs (e.g. a module whose specs are all JVM-bound) must
    // not fail on empty discovery.
    tasks.withType<AbstractTestTask>().configureEach {
        if (this !is Test) {
            failOnNoDiscoveredTests.set(false)
        }
    }
}

// Inline equivalent of the com.pambrose.kotlinter convention plugin.
fun Project.configureKotlinterForKmp() {
    extensions.configure<KotlinterExtension> {
        reporters = arrayOf("checkstyle", "plain")
    }
    // The kotest plugin generates spec-launcher sources via KSP that do not follow
    // the ktlint style; only hand-written sources should be linted.
    val buildDirFile = layout.buildDirectory.get().asFile
    tasks.withType<ConfigurableKtLintTask>().configureEach {
        exclude { it.file.startsWith(buildDirFile) }
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
    // Wire the aggregate tasks to every per-source-set task by type so analysis runs
    // with full type resolution on both kotlin/jvm modules (detektMain/detektTest) and
    // KMP modules (detektJvmMain, detektMetadataCommonMain, ...).
    tasks.named("detekt") {
        dependsOn(tasks.withType<Detekt>().matching { it.name != "detekt" })
    }
    tasks.named("detektBaseline") {
        dependsOn(tasks.withType<DetektCreateBaselineTask>().matching { it.name != "detektBaseline" })
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
                    classes(koverExcludeClasses)
                }
            }
        }
    }
}

fun Project.configurePublishing(isKmp: Boolean) {
    extensions.configure<MavenPublishBaseExtension> {
        val javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")
        val sourcesJar = SourcesJar.Sources()
        configure(
            if (isKmp)
                KotlinMultiplatform(javadocJar = javadocJar, sourcesJar = sourcesJar)
            else
                KotlinJvm(javadocJar = javadocJar, sourcesJar = sourcesJar),
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

fun Project.configureVersions() {
    // A pre-release qualifier is a `.` or `-` delimiter followed by a known unstable
    // keyword. `m\d` matches milestones (`-M1`/`.M2`) without catching stable classifiers
    // like `-macos`/`-MR1`, and the `[.-]` delimiter catches both dash-style (`-alpha`)
    // and dot-style (Netty's `.Beta1`) qualifiers while leaving `-jre`/`.Final` stable.
    val preReleaseQualifier =
        Regex("""[.-](rc|beta|alpha|m\d|cr|snapshot|eap|dev|milestone|pre)""", RegexOption.IGNORE_CASE)

    fun isNonStable(version: String): Boolean = preReleaseQualifier.containsMatchIn(version)

    tasks.withType<DependencyUpdatesTask>().configureEach {
        notCompatibleWithConfigurationCache("the dependency updates plugin is not compatible with the configuration cache")
        // Reject a pre-release candidate only when the current version is stable. For
        // dependencies we intentionally track on a pre-release line (e.g. a detekt
        // alpha), newer pre-releases are still surfaced as available updates.
        rejectVersionIf {
            isNonStable(candidate.version) && !isNonStable(currentVersion)
        }
    }
}
