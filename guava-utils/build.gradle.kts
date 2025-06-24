plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "guava-utils"

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

    implementation(libs.guava)

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)

}

kotlin {
    jvmToolchain(11)
}
