plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "zipkin-utils"

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

    implementation(libs.brave)
    implementation(libs.zipkin.core)
    implementation(libs.zipkin.reporter)
}

kotlin {
    jvmToolchain(11)
}
