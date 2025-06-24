plugins {
    alias(libs.plugins.kotlin.jvm)
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
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.logging)
    api(libs.logback.classic)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
