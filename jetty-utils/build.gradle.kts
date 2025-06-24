plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "jetty-utils"

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

    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
}

kotlin {
    jvmToolchain(11)
}
