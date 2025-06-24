plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "core-utils"

val versionStr: String by extra

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = project.name
            version = versionStr
            from(components["java"])
        }
    }
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization)

    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform)
}

kotlin {
    jvmToolchain(11)
}
