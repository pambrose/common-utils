description = "script-utils-python"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    implementation(libs.python.scripting)

    testImplementation(libs.kluent)
    testImplementation(kotlin("test"))
}
