plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "exposed-utils"

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
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jodatime)
}

kotlin {
    jvmToolchain(11)
}
