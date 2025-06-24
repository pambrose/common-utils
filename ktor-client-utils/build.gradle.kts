plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "ktor-client-utils"

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

    implementation(libs.ktor.client.core)
}

kotlin {
    jvmToolchain(11)
}
