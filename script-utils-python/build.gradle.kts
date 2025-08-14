description = "script-utils-python"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    api(libs.python.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
