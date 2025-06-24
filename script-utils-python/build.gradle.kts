plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "script-utils-python"

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
    implementation(project(":script-utils-common"))

    implementation(libs.python.scripting)

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform)

}

kotlin {
    jvmToolchain(11)
}
