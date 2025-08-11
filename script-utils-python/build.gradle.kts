description = "script-utils-python"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    implementation(libs.python.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
