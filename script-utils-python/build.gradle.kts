description = "Python scripting integration utilities"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    implementation(libs.python.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
