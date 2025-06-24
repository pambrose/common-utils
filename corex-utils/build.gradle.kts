plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "corex-utils"

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
    // implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
}

kotlin {
    jvmToolchain(11)
}
