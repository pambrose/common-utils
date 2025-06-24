plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "prometheus-utils"

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

    implementation(libs.prometheus.core)
    implementation(libs.prometheus.hotspot)
}

kotlin {
    jvmToolchain(11)
}
